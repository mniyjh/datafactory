@echo off
title Web Search Proxy
cd /d "%~dp0..\tools\web-search-proxy"

where node >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Node.js not found. Install: https://nodejs.org/
    pause
    exit /b 1
)

echo === Web Search Proxy ===
set /p API_KEY="DeepSeek API Key: "
if "%API_KEY%"=="" (
    echo [ERROR] API Key is required
    pause
    exit /b 1
)

echo.
echo http://127.0.0.1:8765
echo.
set DEEPSEEK_API_KEY=%API_KEY%
node proxy.mjs
pause
