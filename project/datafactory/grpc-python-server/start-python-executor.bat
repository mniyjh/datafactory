@echo off
title Python Executor gRPC Server
setlocal

cd /d "%~dp0"

echo ----------------------------------------
echo   Python gRPC Executor - Quick Start
echo ----------------------------------------
echo.

echo [1/3] Checking Python...
python --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python not found. Install Python 3.8+
    pause
    exit /b 1
)
python --version
echo.

echo [2/3] Installing dependencies...
pip install -r requirements.txt --quiet 2>nul
if errorlevel 1 (
    echo [WARN] Retrying with mirror...
    pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple --quiet
    if errorlevel 1 (
        echo [ERROR] pip install failed
        pause
        exit /b 1
    )
)
echo        Done: grpcio + grpcio-tools
echo.

echo [3/3] Starting gRPC server on port 50051...
echo ----------------------------------------
echo   Address: 127.0.0.1:50051
echo   Stop:    Ctrl+C
echo ----------------------------------------
echo.

python server.py --port 50051

if errorlevel 1 (
    echo.
    echo [ERROR] Server exited with code: %errorlevel%
    pause
)
endlocal
