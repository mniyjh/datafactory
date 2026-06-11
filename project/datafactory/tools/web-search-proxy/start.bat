@echo off
REM DeepSeek Web Search Proxy - Windows 启动脚本
REM 在 Claude Code 启动前运行此脚本，或设为后台自启动

cd /d "%~dp0"

echo ========================================================
echo   DeepSeek Web Search Proxy Launcher
echo ========================================================
echo.
echo   Starting proxy on http://127.0.0.1:8765 ...
echo   Press Ctrl+C to stop
echo.
echo ========================================================
echo.

node proxy.mjs

pause
