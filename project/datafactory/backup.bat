@echo off
REM DataFactory 数据库备份脚本
REM 用法: 双击运行，或添加到 Windows 计划任务每天执行
REM 备份文件: backup\datafactory_20260611_120000.sql

set BACKUP_DIR=%~dp0backup
set MYSQL_USER=root
set MYSQL_PASS=123456
set DB_NAME=datafactory

if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

set TIMESTAMP=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%
set FILE=%BACKUP_DIR%\%DB_NAME%_%TIMESTAMP%.sql

echo Backing up %DB_NAME% to %FILE% ...
mysqldump -u%MYSQL_USER% -p%MYSQL_PASS% --single-transaction --routines --triggers %DB_NAME% > "%FILE%"

if %ERRORLEVEL% EQU 0 (
    echo OK: %FILE%
) else (
    echo FAILED
    pause
)

REM 保留最近 7 天的备份，删除旧的
forfiles /p "%BACKUP_DIR%" /m "*.sql" /d -7 /c "cmd /c del @file" 2>nul
