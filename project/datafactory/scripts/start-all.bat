@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"
cd ..
set "ROOT=%cd%"

:: Service definitions
set "NAME1=Gateway"
set "NAME2=Config"
set "NAME3=Executor"
set "NAME4=Python"
set "NAME5=Frontend"
set "PORT1=8080"
set "PORT2=8081"
set "PORT3=8082"
set "PORT4=50051"
set "PORT5=5173"
set "JAR1=%ROOT%\datafactory-backend-gateway\target\datafactory-backend-gateway-1.0.0-SNAPSHOT.jar"
set "JAR2=%ROOT%\datafactory-backend-configuration\target\datafactory-backend-configuration-1.0.0-SNAPSHOT.jar"
set "JAR3=%ROOT%\datafactory-backend-executor\datafactory-backend-executor-server\target\datafactory-backend-executor-server-1.0.0-SNAPSHOT.jar"
set "MVN_MODULES=datafactory-backend-gateway,datafactory-backend-configuration"

:: CLI mode: if args passed, dispatch and exit (no pauses)
if not "%~1"=="" (
    set "INTERACTIVE=0"
    call :dispatch %*
    exit /b 0
)

:: Interactive loop
:loop
set "INTERACTIVE=1"
cls
call :banner
call :status_bar
echo.
echo   a=start-all   q=stop-all   r=restart   b=build+run
echo   1=GW  2=CFG  3=EXE  4=PY  5=FE
echo   s1-s5=stop  r1-r5=restart  s=status  ?=help  x=exit
echo.
set "cmd="
set /p "cmd= >> "
if "!cmd!"=="" goto loop
call :dispatch !cmd!
if /i "!cmd!"=="x" exit /b 0
echo.
echo   Press any key to return to menu...
pause >nul
goto loop


:: ===== DISPATCHER =====
:dispatch
set "c=%~1"
if "%c%"=="a"  ( call :start_all & exit /b )
if "%c%"=="q"  ( call :stop_all  & exit /b )
if "%c%"=="r"  ( call :do_restart_all & exit /b )
if "%c%"=="b"  ( call :do_build_run   & exit /b )
if "%c%"=="s"  ( call :status_table   & exit /b )
if "%c%"=="?"  ( call :help           & exit /b )
if "%c%"=="x"  ( cls & echo Bye. & exit /b 0 )

:: 1~5 start one
for /L %%i in (1,1,5) do (
    if "%c%"=="%%i" ( call :start_svc %%i & exit /b )
)
:: s1~s5 stop one, r1~r5 restart one
set "pfx=%c:~0,1%"
set "num=%c:~1%"
if "%pfx%"=="s" if %num% geq 1 if %num% leq 5 (
    call :stop_svc %num% & exit /b
)
if "%pfx%"=="r" if %num% geq 1 if %num% leq 5 (
    call :stop_svc %num%
    timeout /t 1 /nobreak >nul
    call :start_svc %num%
    exit /b
)

echo  Unknown: %c%  (type ? for help)
timeout /t 2 /nobreak >nul
exit /b


:: ===== BANNER =====
:banner
echo.
echo   +------------------------------------------+
echo   ^|       DataFactory Control Panel          ^|
echo   +------------------------------------------+
exit /b


:: ===== HELP =====
:help
echo.
echo  Commands:
echo    a                Start ALL services
echo    q                Stop ALL services
echo    r                Restart ALL (stop + start)
echo    b                Build (mvn) + restart ALL
echo    1 / 2 / 3 / 4 / 5  Start: Gateway / Config / Executor / Python / Frontend
echo    s1..s5            Stop one service
echo    r1..r5            Restart one service
echo    s                Full status table
echo    x                Exit
exit /b


:: ===== STATUS BAR (one-liner) =====
:status_bar
set "line="
for /L %%i in (1,1,5) do (
    call :is_running %%i
    if !errorlevel! equ 0 (
        set "line=!line!  !NAME%%i!=UP"
    ) else (
        set "line=!line!  !NAME%%i!=--"
    )
)
echo  !line!
exit /b


:: ===== STATUS TABLE =====
:status_table
echo.
echo  +----------------------+-------+--------+
echo  ^| Service              ^| Port  ^| Status ^|
echo  +----------------------+-------+--------+
for /L %%i in (1,1,5) do (
    set "sname=!NAME%%i!        "
    call :is_running %%i
    if !errorlevel! equ 0 (
        echo  ^| !sname:~0,20! ^| !PORT%%i!  ^| UP     ^|
    ) else (
        echo  ^| !sname:~0,20! ^| !PORT%%i!  ^| --     ^|
    )
)
echo  +----------------------+-------+--------+
exit /b


:: ===== CHECK IF SERVICE IS RUNNING (by port) =====
:is_running
set "pt=!PORT%1!"
netstat -ano 2>nul | find "LISTENING" | find ":!pt! " >nul
exit /b %errorlevel%


:: ===== START ALL =====
:start_all
echo.
for /L %%i in (1,1,5) do ( call :start_svc %%i )
echo.
echo  Done.
timeout /t 2 /nobreak >nul
exit /b


:: ===== START ONE =====
:start_svc
set "n=%~1"
call :is_running %n%
if !errorlevel! equ 0 (
    echo  [%n%] !NAME%n%! already UP
    exit /b
)
echo  [%n%] Starting !NAME%n%! ...

if "%n%"=="1" (
    if not exist "!JAR1!" ( echo   ERROR: JAR not found & exit /b )
    start "gw" java -jar "!JAR1!"
    exit /b
)
if "%n%"=="2" (
    if not exist "!JAR2!" ( echo   ERROR: JAR not found & exit /b )
    call :load_env SMTP_USERNAME
    call :load_env SMTP_PASSWORD
    start "cfg" java -Dspring.mail.username="!SMTP_USERNAME!" -Dspring.mail.password="!SMTP_PASSWORD!" -jar "!JAR2!"
    exit /b
)
if "%n%"=="3" (
    if not exist "!JAR3!" ( echo   ERROR: JAR not found & exit /b )
    start "exe" java -jar "!JAR3!"
    exit /b
)
if "%n%"=="4" (
    if exist "%ROOT%\grpc-python-server\start-python-executor.bat" (
        start "py" cmd /c "cd /d "%ROOT%\grpc-python-server" && call start-python-executor.bat"
    ) else (
        echo   SKIP: not found
    )
    exit /b
)
if "%n%"=="5" (
    node -v >nul 2>&1 || ( echo   SKIP: Node.js not found & exit /b )
    start "fe" cmd /c "cd /d "%ROOT%\frontend" && npm run dev"
    exit /b
)
exit /b


:: ===== STOP ALL =====
:stop_all
echo.
for /L %%i in (1,1,5) do ( call :stop_svc %%i )
echo.
echo  All stopped.
timeout /t 2 /nobreak >nul
exit /b


:: ===== STOP ONE (kill by port) =====
:stop_svc
set "n=%~1"
set "pt=!PORT%n%!"
echo  [%n%] Stopping !NAME%n%! (port !pt!) ...
call :kill_port !pt!
exit /b


:: ===== KILL PROCESS ON PORT =====
:kill_port
set "p=%~1"
set "found="
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| find "LISTENING" ^| find ":%p% "') do (
    set "found=1"
    taskkill /F /PID %%a 2>nul && echo   Killed PID %%a on port %p%
)
if not defined found echo   Not running
exit /b


:: ===== DO RESTART ALL =====
:do_restart_all
call :stop_all
timeout /t 2 /nobreak >nul
call :start_all
exit /b


:: ===== DO BUILD + RESTART =====
:do_build_run
call :stop_all
timeout /t 1 /nobreak >nul
call :build
if errorlevel 1 exit /b
call :start_all
exit /b


:: ===== LOAD ENV FROM REGISTRY =====
:load_env
if defined %1 exit /b
for /f "tokens=2,*" %%a in ('reg query "HKCU\Environment" /v %1 2^>nul ^| find "%1"') do set "%1=%%b"
exit /b

:: ===== MAYBE PAUSE =====
:maybe_pause
if "%INTERACTIVE%"=="0" exit /b
pause >nul
exit /b


:: ===== BUILD =====
:build
echo.
echo  Building: %MVN_MODULES%
echo.
mvn -version >nul 2>&1 || (
    echo  [ERROR] Maven not found
    timeout /t 3 /nobreak >nul
    exit /b 1
)
cd /d "%ROOT%"
mvn package -DskipTests -pl %MVN_MODULES% -am -q
if errorlevel 1 (
    echo.
    echo  [ERROR] Build failed
    call :maybe_pause
    exit /b 1
)
echo  Build: OK
timeout /t 2 /nobreak >nul
exit /b 0
