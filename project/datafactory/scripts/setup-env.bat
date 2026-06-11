@echo off
title DataFactory Env Setup

echo.
echo ========================================
echo   DataFactory Environment Setup
echo ========================================
echo.

set /p SMTP_USER="QQ Email (e.g. 1106935659@qq.com): "
set /p SMTP_PASS="QQ SMTP Auth Code (16 chars): "

echo.
echo --- MySQL (press Enter for defaults) ---
set /p DB_USER="MySQL Username [root]: "
if "%DB_USER%"=="" set DB_USER=root
set /p DB_PASS="MySQL Password [123456]: "
if "%DB_PASS%"=="" set DB_PASS=123456

echo.
echo Setting environment variables...
setx SMTP_USERNAME "%SMTP_USER%"
setx SMTP_PASSWORD "%SMTP_PASS%"
setx DB_USERNAME "%DB_USER%"
setx DB_PASSWORD "%DB_PASS%"

echo.
echo ========================================
echo   Done!
echo ========================================
echo.
echo   SMTP_USERNAME = %SMTP_USER%
echo   DB_USERNAME   = %DB_USER%
echo.
echo   Restart IDEA to take effect.
echo.
pause
