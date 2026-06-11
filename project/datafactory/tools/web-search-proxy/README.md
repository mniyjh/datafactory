# DeepSeek Web Search Proxy

在 Claude Code 和 DeepSeek API 之间的本地 HTTP 代理，自动为联网搜索请求注入 `web_search` 工具。

## 快速开始

```bash
# 1. 启动代理
cd tools/web-search-proxy
node proxy.mjs

# 2. 在 Claude Code 中使用
# 消息中包含 "请联网搜索" 即可触发
```

## 工作原理

```
Claude Code → localhost:8765 (代理) → api.deepseek.com/anthropic
                  │
                  ├─ 检测消息中的触发关键词
                  ├─ 匹配时注入 web_search_20250305 工具
                  └─ 流式转发请求/响应
```

## 触发关键词

消息中包含以下任一短语即启用联网搜索：

| 中文 | 英文 |
|------|------|
| 请联网搜索 | web search |
| 联网搜索 | search the web |
| 搜索一下 | search for |
| 帮我搜索 | look up online |
| 查一下 | do a web search |
| 帮我查 | |
| 上网查 | |
| 网上查 | |

## 配置

通过环境变量自定义：

```bash
# 修改监听端口 (默认 8765)
set WS_PROXY_PORT=9999

# 修改目标 API 地址
set WS_PROXY_TARGET=https://api.deepseek.com/anthropic

node proxy.mjs
```

## 健康检查

```bash
curl http://127.0.0.1:8765/health
```

## 回滚

如需停用代理，删除项目中的 `.claude/settings.local.json` 文件（或移除其中的 `ANTHROPIC_BASE_URL` 配置）即可恢复直连。

## 要求

- Node.js >= 18 (零外部依赖)
