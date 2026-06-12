// DeepSeek Web Search Proxy — 本地代理转发 WebSearch 请求
// 启动: node proxy.mjs
// 端口: 8765

import http from 'http';

const PORT = 8765;
const TARGET = 'https://api.deepseek.com';
// DeepSeek 兼容 OpenAI anthropic 格式的 web_search 扩展
const API_PATH = '/anthropic/v1/messages';

const DEEPSEEK_API_KEY = process.env.DEEPSEEK_API_KEY || '';

const server = http.createServer(async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  if (req.method !== 'POST') {
    res.writeHead(405, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Only POST is supported' }));
    return;
  }

  let body = '';
  req.on('data', chunk => body += chunk);
  req.on('end', async () => {
    try {
      const payload = JSON.parse(body);
      // 注入 web_search 工具到请求中
      if (!payload.tools) payload.tools = [];
      payload.tools.push({
        type: 'web_search_20260209',
        web_search_20260209: { enabled: true }
      });

      const upstream = await fetch(TARGET + API_PATH, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${DEEPSEEK_API_KEY}`,
        },
        body: JSON.stringify(payload),
      });

      const data = await upstream.text();
      res.writeHead(upstream.status, { 'Content-Type': 'application/json' });
      res.end(data);
      console.log(`[${new Date().toISOString()}] ${upstream.status} ${body.length} bytes`);
    } catch (e) {
      console.error('Proxy error:', e.message);
      res.writeHead(502, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: e.message }));
    }
  });
});

server.listen(PORT, () => {
  console.log(`Web Search Proxy running on http://127.0.0.1:${PORT}`);
  console.log(`Target: ${TARGET}${API_PATH}`);
});
