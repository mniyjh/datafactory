@echo off
title DeepSeek Web Search Proxy
cd /d "%~dp0tools\web-search-proxy"

echo.
echo  ============================================================
echo    DeepSeek Web Search Proxy - 一键启动
echo  ============================================================
echo.
echo    代理地址: http://127.0.0.1:8765
echo    目标 API:  https://api.deepseek.com/anthropic
echo.
echo    在 Claude Code 中输入 "请联网搜索" 即可触发
echo  ============================================================
echo.

REM 检查 node 是否可用
where node >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo  [ERROR] 未找到 Node.js，请先安装: https://nodejs.org/
    pause
    exit /b 1
)

echo  [INFO] 正在启动代理...
echo.

node proxy.mjs

REM 如果 node 意外退出
echo.
echo  [INFO] 代理已停止。
pause
