/**
 * DeepSeek Web Search Proxy
 *
 * 在 Claude Code 和 DeepSeek API 之间的本地 HTTP 代理。
 * 当用户消息包含触发关键词时，自动向请求中注入 web_search 工具，
 * 并过滤响应中的非标准 content block 类型以确保 Claude Code 兼容性。
 *
 * 用法: node proxy.mjs
 * 环境变量:
 *   WS_PROXY_PORT   - 代理监听端口 (默认 8765)
 *   WS_PROXY_TARGET - DeepSeek API 地址 (默认 https://api.deepseek.com/anthropic)
 */

import http from 'node:http';
import https from 'node:https';

// -- 配置 ----------------------------------------------------------------

const PORT = parseInt(process.env.WS_PROXY_PORT || '8765', 10);
const TARGET = process.env.WS_PROXY_TARGET || 'https://api.deepseek.com/anthropic';

const TRIGGER_KEYWORDS = [
  '请联网搜索', '联网搜索', '搜索一下', '帮我搜索',
  '查一下', '帮我查', '上网查', '网上查',
  'web search', 'search the web', 'search for',
  'look up online', 'do a web search',
];

// HTTP/1.1 hop-by-hop headers — proxies should NOT forward these
const HOP_BY_HOP = new Set([
  'connection', 'keep-alive', 'proxy-connection',
  'transfer-encoding', 'te', 'trailer', 'upgrade',
  'proxy-authorization', 'proxy-authenticate',
]);

// DeepSeek 特有的 content block 类型，Claude Code 无法解析
// 需要在响应中过滤掉，只保留标准 Anthropic 类型 (text, thinking, tool_use)
const NON_STANDARD_TYPES = new Set([
  'server_tool_use',
  'web_search_tool_result',
]);

// 标准 Anthropic content block 类型
const STANDARD_TYPES = new Set([
  'text', 'thinking', 'tool_use', 'image', 'document',
]);

const TARGET_URL = new URL(TARGET);

// -- Utilities -----------------------------------------------------------

const timestamp = () => new Date().toLocaleString('zh-CN', { hour12: false });
const log = (msg) => console.log(`[${timestamp()}] ${msg}`);

/** Build clean forward headers */
function buildForwardHeaders(reqHeaders) {
  const headers = {};
  for (const [key, value] of Object.entries(reqHeaders)) {
    if (!HOP_BY_HOP.has(key.toLowerCase())) headers[key] = value;
  }
  headers['host'] = TARGET_URL.hostname;
  delete headers['content-length']; // body may be modified
  return headers;
}

/** Build clean response headers */
function buildResponseHeaders(proxyResHeaders) {
  const headers = {};
  for (const [key, value] of Object.entries(proxyResHeaders)) {
    if (!HOP_BY_HOP.has(key.toLowerCase())) headers[key] = value;
  }
  return headers;
}

/** Extract text from message content (handles string and content-block-array) */
function extractText(content) {
  if (typeof content === 'string') return content;
  if (Array.isArray(content)) {
    return content.filter(b => b.type === 'text').map(b => b.text).join(' ');
  }
  return '';
}

/** Check if any user message contains a trigger keyword */
function shouldEnableWebSearch(messages) {
  if (!messages) return { triggered: false, keyword: null };
  for (const msg of messages.filter(m => m.role === 'user')) {
    const text = extractText(msg.content);
    for (const kw of TRIGGER_KEYWORDS) {
      if (text.includes(kw)) return { triggered: true, keyword: kw };
    }
  }
  return { triggered: false, keyword: null };
}

/** Inject web_search tool into the request body */
function injectWebSearchTool(body) {
  if (!body.tools) body.tools = [];
  if (!body.tools.some(t => t.type?.startsWith('web_search_'))) {
    body.tools.push({ type: 'web_search_20250305', name: 'web_search' });
  }
  return body;
}

// -- Response Sanitizer --------------------------------------------------

/**
 * 过滤非标准 content block 类型，确保 Claude Code 兼容性。
 *
 * 非流式：直接过滤 content 数组
 * 流式 (SSE)：逐行解析，抑制非标准 content block 的 start/delta/stop 事件
 */

/** 过滤非流式响应中的 content 数组 */
function sanitizeNonStreaming(body) {
  if (!body.content) return body;
  body.content = body.content.filter(block => STANDARD_TYPES.has(block.type));
  return body;
}

/**
 * 创建 SSE 流过滤器。
 * 返回一个 Transform-like 对象，包含 write(line) 和 flush() 方法。
 */
function createSSEFilter() {
  let buffer = '';             // 行缓冲
  const suppressed = new Set(); // 被抑制的 content block index

  function processLine(line) {
    if (!line) return '\n';
    if (!line.startsWith('data: ')) return line + '\n';

    try {
      const event = JSON.parse(line.slice(6));
      const et = event.type;

      // content_block_start: check non-standard type -> suppress
      if (et === 'content_block_start') {
        const blockType = event.content_block?.type;
        if (NON_STANDARD_TYPES.has(blockType)) {
          suppressed.add(event.index);
          return null;
        }
      }

      // skip events for suppressed block indices
      if (event.index !== undefined && suppressed.has(event.index)) {
        if (et === 'content_block_stop') suppressed.delete(event.index);
        return null;
      }

      // message_delta: strip usage.server_tool_use
      if (et === 'message_delta' && event.usage?.server_tool_use) {
        delete event.usage.server_tool_use;
        return 'data: ' + JSON.stringify(event) + '\n';
      }

      // message_start: strip non-standard content blocks
      if (et === 'message_start' && event.message?.content) {
        event.message.content = event.message.content.filter(
          block => STANDARD_TYPES.has(block.type)
        );
        return 'data: ' + JSON.stringify(event) + '\n';
      }

      return line + '\n';
    } catch {
      return line + '\n';
    }
  }

  return {
    write(chunk) {
      buffer += chunk.toString();
      const lines = buffer.split('\n');
      // 最后一行可能不完整，保留在 buffer
      buffer = lines.pop();
      return lines.map(processLine).filter(Boolean).join('');
    },
    flush() {
      // 处理残留
      if (buffer) {
        const result = processLine(buffer.trimEnd());
        buffer = '';
        return result || '';
      }
      return '';
    },
  };
}

// -- Proxy Server --------------------------------------------------------

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');

  if (req.method === 'OPTIONS') {
    res.writeHead(204); res.end(); return;
  }

  // Health check
  if (req.method === 'GET' && (req.url === '/' || req.url === '/health')) {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      status: 'ok',
      proxy: 'DeepSeek Web Search Proxy',
      target: TARGET,
      keywords: TRIGGER_KEYWORDS.length,
      sanitize: [...NON_STANDARD_TYPES],
    }));
    return;
  }

  // Collect request body
  let rawBody = '';
  req.on('data', chunk => { rawBody += chunk; });
  req.on('end', () => {
    const isMessages = req.url?.includes('/messages');
    let webSearchEnabled = false;

    // Inject web_search tool when trigger keyword detected
    if (isMessages && req.method === 'POST' && rawBody) {
      try {
        const body = JSON.parse(rawBody);
        const { triggered, keyword } = shouldEnableWebSearch(body.messages);
        if (triggered) {
          injectWebSearchTool(body);
          rawBody = JSON.stringify(body);
          webSearchEnabled = true;
          log(`[WEB_SEARCH] trigger: "${keyword}"`);
        } else {
          log(`[PASSTHROUGH]`);
        }
      } catch (e) {
        log(`[WARN] Failed to parse body: ${e.message}`);
      }
    }

    // Forward to DeepSeek
    const forwardPath = TARGET_URL.pathname.replace(/\/$/, '') + req.url;

    const proxyReq = https.request({
      hostname: TARGET_URL.hostname,
      port: TARGET_URL.port || 443,
      path: forwardPath,
      method: req.method,
      headers: buildForwardHeaders(req.headers),
    }, (proxyRes) => {
      const contentType = proxyRes.headers['content-type'] || '';
      const isStreaming = contentType.includes('text/event-stream');

      if (webSearchEnabled && !isStreaming) {
        // --- 非流式 + web search: buffer + sanitize ---
        const chunks = [];
        proxyRes.on('data', chunk => chunks.push(chunk));
        proxyRes.on('end', () => {
          try {
            const body = JSON.parse(Buffer.concat(chunks).toString());
            sanitizeNonStreaming(body);
            const clean = JSON.stringify(body);
            const resHeaders = buildResponseHeaders(proxyRes.headers);
            resHeaders['content-length'] = Buffer.byteLength(clean);
            res.writeHead(proxyRes.statusCode, resHeaders);
            res.end(clean);
          } catch {
            res.writeHead(proxyRes.statusCode, buildResponseHeaders(proxyRes.headers));
            res.end(Buffer.concat(chunks));
          }
        });
      } else if (webSearchEnabled && isStreaming) {
        // --- 流式 + web search: SSE line-by-line filter ---
        res.writeHead(proxyRes.statusCode, buildResponseHeaders(proxyRes.headers));
        const filter = createSSEFilter();
        proxyRes.on('data', chunk => {
          const clean = filter.write(chunk);
          if (clean) res.write(clean);
        });
        proxyRes.on('end', () => {
          const remaining = filter.flush();
          if (remaining) res.write(remaining);
          res.end();
        });
      } else {
        // --- 无 web search: 直接透传 ---
        res.writeHead(proxyRes.statusCode, buildResponseHeaders(proxyRes.headers));
        proxyRes.pipe(res);
      }
    });

    proxyReq.on('error', (err) => {
      log(`[ERROR] ${err.message}`);
      if (!res.headersSent) {
        res.writeHead(502, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'bad_gateway', message: err.message }));
      }
    });

    proxyReq.setTimeout(5 * 60 * 1000, () => {
      proxyReq.destroy();
      if (!res.headersSent) {
        res.writeHead(504, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'gateway_timeout' }));
      }
    });

    proxyReq.write(rawBody);
    proxyReq.end();
  });

  req.on('error', (err) => {
    log(`[ERROR] Request: ${err.message}`);
    if (!res.headersSent) { res.writeHead(400); res.end(); }
  });
});

// -- Startup -------------------------------------------------------------

server.listen(PORT, '127.0.0.1', () => {
  console.log('='.repeat(56));
  console.log('  DeepSeek Web Search Proxy');
  console.log('='.repeat(56));
  console.log(`  Listening:   http://127.0.0.1:${PORT}`);
  console.log(`  Target:      ${TARGET}`);
  console.log(`  Triggers:    ${TRIGGER_KEYWORDS.length} keywords`);
  console.log(`  Sanitize:    ${[...NON_STANDARD_TYPES].join(', ')}`);
  console.log(`  Health:      http://127.0.0.1:${PORT}/health`);
  console.log('='.repeat(56));
  console.log('');
  console.log('  Include "请联网搜索" or "web search" in your');
  console.log('  message to enable web search.');
  console.log('');
});

process.on('SIGINT', () => { log('Shutting down...'); server.close(() => process.exit(0)); });
process.on('SIGTERM', () => { log('Shutting down...'); server.close(() => process.exit(0)); });
