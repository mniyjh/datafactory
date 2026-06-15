@echo off
title DataFactory — 一键启动
setlocal enabledelayedexpansion

REM 切换到项目根目录
pushd "%~dp0.."
set "ROOT=%cd%"
popd
cd /d "%ROOT%"

echo.
echo ============================================
echo   DataFactory — 启动全部服务
echo ============================================
echo   Root: %ROOT%
echo.

REM ─── 检查 Java ───
echo [0/5] Checking environment...
where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found. Install JDK 21+
    pause & exit /b 1
)
java -version 2>&1 | findstr /i "version"

where node >nul 2>&1
if errorlevel 1 (
    echo   Node: NOT FOUND - frontend skipped
    set "HAS_NODE=0"
) else (
    node -v
    set "HAS_NODE=1"
)

REM ─── 检查 JAR ───
if not exist "%ROOT%\datafactory-backend-gateway\target\datafactory-backend-gateway-1.0.0-SNAPSHOT.jar" (
    echo [ERROR] JAR files missing. Run: mvn package -DskipTests
    pause & exit /b 1
)
echo   JARs: OK
echo.

echo   1. Gateway       :8080
echo   2. Configuration :8081
echo   3. Executor       :8082
echo   4. Python gRPC    :50051
echo   5. Frontend       :5173
echo ============================================

REM ─── 1 ───
echo [1/5] Gateway...
start "Gateway-8080" java -jar "%ROOT%\datafactory-backend-gateway\target\datafactory-backend-gateway-1.0.0-SNAPSHOT.jar"
echo        OK

REM ─── 2 ───
echo [2/5] Configuration...
start "Config-8081" java -jar "%ROOT%\datafactory-backend-configuration\target\datafactory-backend-configuration-1.0.0-SNAPSHOT.jar"
echo        OK

REM ─── 3 ───
echo [3/5] Executor...
start "Executor-8082" java -jar "%ROOT%\datafactory-backend-executor\datafactory-backend-executor-server\target\datafactory-backend-executor-server-1.0.0-SNAPSHOT.jar"
echo        OK

REM ─── 4 ───
echo [4/5] Python gRPC...
if exist "%ROOT%\grpc-python-server\start-python-executor.bat" (
    start "Python-gRPC" cmd /c "cd /d "%ROOT%\grpc-python-server" && call start-python-executor.bat"
    echo        OK
) else (
    echo        SKIP: not found
)

REM ─── 5 ───
echo [5/5] Frontend...
if "%HAS_NODE%"=="1" (
    start "Frontend-5173" cmd /c "cd /d "%ROOT%\frontend" && npm run dev"
    echo        OK
) else (
    echo        SKIP: Node.js not found
)

echo.
echo ============================================
echo   Done!
echo   Gateway:  http://127.0.0.1:8080
echo   Frontend: http://localhost:5173
echo ============================================
echo.
pause
