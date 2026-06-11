@echo off
REM DataFactory 数据库恢复脚本
REM 用法: restore.bat backup\datafactory_20260611_120000.sql

if "%1"=="" (
    echo Usage: restore.bat backup\xxx.sql
    pause
    exit /b 1
)

set MYSQL_USER=root
set MYSQL_PASS=123456
set DB_NAME=datafactory
set FILE=%1

echo WARNING: This will DROP and recreate %DB_NAME%!
echo File: %FILE%
echo.
set /p CONFIRM="Type YES to continue: "
if not "%CONFIRM%"=="YES" (
    echo Cancelled.
    pause
    exit /b 0
)

mysql -u%MYSQL_USER% -p%MYSQL_PASS% -e "DROP DATABASE IF EXISTS %DB_NAME%; CREATE DATABASE %DB_NAME% DEFAULT CHARACTER SET utf8mb4;"
mysql -u%MYSQL_USER% -p%MYSQL_PASS% %DB_NAME% < "%FILE%"

if %ERRORLEVEL% EQU 0 (
    echo RESTORE OK
) else (
    echo RESTORE FAILED
)
pause
