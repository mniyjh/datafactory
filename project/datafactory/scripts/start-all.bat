@echo off
title DataFactory — 一键启动
setlocal enabledelayedexpansion

REM 切换到项目根目录
cd /d "%~dp0.."
set "ROOT=%cd%"

echo.
echo ============================================
echo   DataFactory — 启动全部服务
echo ============================================
echo   Root: %ROOT%
echo.

REM ─── 检查 Java ───
echo [0/5] Checking environment...
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found. Install JDK 21+
    pause & exit /b 1
)
for /f "tokens=3 delims= " %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do echo   Java: %%v

node -v >nul 2>&1
if errorlevel 1 (
    echo   Node: NOT FOUND — frontend will be skipped
) else (
    for /f "delims=" %%v in ('node -v') do echo   Node: %%v
)

REM ─── 检查 JAR 文件 ───
if not exist "datafactory-backend-gateway\target\datafactory-backend-gateway-1.0.0-SNAPSHOT.jar" (
    echo [ERROR] JAR files not found. Run "mvn package -DskipTests" first.
    pause & exit /b 1
)

echo.
echo   启动顺序:
echo     1. Gateway       :8080
echo     2. Configuration :8081
echo     3. Executor       :8082
echo     4. Python gRPC    :50051
echo     5. Frontend       :5173
echo ============================================

REM ─── 1. Gateway ───
echo.
echo [1/5] Starting Gateway (:8080) ...
start "Gateway-8080" /d "%ROOT%" java -jar "%ROOT%\datafactory-backend-gateway\target\datafactory-backend-gateway-1.0.0-SNAPSHOT.jar"
echo        OK

REM ─── 2. Configuration ───
echo [2/5] Starting Configuration (:8081) ...
start "Config-8081" /d "%ROOT%" java -jar "%ROOT%\datafactory-backend-configuration\target\datafactory-backend-configuration-1.0.0-SNAPSHOT.jar"
echo        OK

REM ─── 3. Executor ───
echo [3/5] Starting Executor (:8082) ...
start "Executor-8082" /d "%ROOT%" java -jar "%ROOT%\datafactory-backend-executor\datafactory-backend-executor-server\target\datafactory-backend-executor-server-1.0.0-SNAPSHOT.jar"
echo        OK

REM ─── 4. Python gRPC ───
echo [4/5] Starting Python gRPC (:50051) ...
if exist "%ROOT%\grpc-python-server\start-python-executor.bat" (
    start "Python-gRPC-50051" cmd /c "cd /d "%ROOT%\grpc-python-server" && start-python-executor.bat"
    echo        OK
) else (
    echo        [SKIP] grpc-python-server not found
)

REM ─── 5. Frontend ───
echo [5/5] Starting Frontend (:5173) ...
node -v >nul 2>&1
if errorlevel 1 (
    echo        [SKIP] Node.js not found
) else (
    start "Frontend-5173" cmd /c "cd /d "%ROOT%\frontend" && npm run dev"
    echo        OK
)

echo.
echo ============================================
echo   All services started!
echo ============================================
echo.
echo   Gateway:       http://127.0.0.1:8080
echo   Configuration: http://127.0.0.1:8081
echo   Executor:      http://127.0.0.1:8082
echo   Python gRPC:   127.0.0.1:50051
echo   Frontend:      http://localhost:5173
echo.
echo   Close each window to stop the service.
echo ============================================
echo.
pause
