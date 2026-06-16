@echo off
setlocal enabledelayedexpansion
title DataFactory Env Setup

cd /d "%~dp0"

:: Load current values from registry for display
call :load_reg SMTP_USERNAME
call :load_reg SMTP_PASSWORD
call :load_reg DB_USERNAME
call :load_reg DB_PASSWORD

:: If no saved config, enter setup flow
if "%SMTP_USERNAME%"=="" if "%DB_USERNAME%"=="" goto setup

:: ===== INTERACTIVE LOOP =====
:loop
cls
call :banner
call :show_config
echo.
echo   n=new-config   s=status   x=exit(clean SMTP)
echo.
set "cmd="
set /p "cmd= >> "
if "!cmd!"=="" goto loop
if /i "!cmd!"=="n" goto setup
if /i "!cmd!"=="s" goto status_full
if /i "!cmd!"=="x" goto cleanup
echo   Unknown: !cmd!
timeout /t 2 /nobreak >nul
goto loop


:: ===== SETUP =====
:setup
echo.
echo   Enter new configuration (press Enter to keep current):
echo.
set /p u="  QQ Email   [%SMTP_USERNAME%]: "
if not "%u%"=="" set "SMTP_USERNAME=%u%"
set /p p="  SMTP Code  [%SMTP_PASSWORD%]: "
if not "%p%"=="" set "SMTP_PASSWORD=%p%"
echo.
set /p du="  MySQL User [%DB_USERNAME%]: "
if not "%du%"=="" set "DB_USERNAME=%du%"
set /p dp="  MySQL Pass [%DB_PASSWORD%]: "
if not "%dp%"=="" set "DB_PASSWORD=%dp%"

echo.
echo   Writing to registry...
setx SMTP_USERNAME "%SMTP_USERNAME%"   >nul
setx SMTP_PASSWORD "%SMTP_PASSWORD%"   >nul
setx DB_USERNAME   "%DB_USERNAME%"     >nul
setx DB_PASSWORD   "%DB_PASSWORD%"     >nul
echo   Done.
timeout /t 2 /nobreak >nul
goto loop


:: ===== FULL STATUS =====
:status_full
call :load_reg SMTP_USERNAME
call :load_reg SMTP_PASSWORD
call :load_reg DB_USERNAME
call :load_reg DB_PASSWORD

echo.
echo   +-------------------+---------------------------+
echo   ^| SMTP_USERNAME     ^| %SMTP_USERNAME%
echo   ^| SMTP_PASSWORD     ^| %SMTP_PASSWORD%
echo   ^| DB_USERNAME       ^| %DB_USERNAME%
echo   ^| DB_PASSWORD       ^| %DB_PASSWORD%
echo   +-------------------+---------------------------+
echo.
pause
goto loop


:: ===== CLEANUP AND EXIT =====
:cleanup
echo.
echo   Cleaning SMTP credentials from registry...
reg delete "HKCU\Environment" /v SMTP_USERNAME /f 2>nul
reg delete "HKCU\Environment" /v SMTP_PASSWORD /f 2>nul
echo   SMTP removed. DB credentials kept.
echo.
echo   Goodbye.
timeout /t 2 /nobreak >nul
exit /b 0


:: ===== HELPERS =====
:banner
echo.
echo   +------------------------------------------+
echo   ^|       DataFactory Env Setup              ^|
echo   +------------------------------------------+
exit /b

:show_config
call :load_reg SMTP_USERNAME
call :load_reg DB_USERNAME
if "%SMTP_USERNAME%"=="" ( set "smtp_disp=--" ) else ( set "smtp_disp=configured" )
if "%DB_USERNAME%"==""   ( set "db_disp=--"    ) else ( set "db_disp=configured"   )
echo   SMTP: !smtp_disp!    DB: !db_disp!
exit /b

:load_reg
set "%1="
for /f "tokens=2,*" %%a in ('reg query "HKCU\Environment" /v %1 2^>nul ^| find "%1"') do set "%1=%%b"
exit /b
