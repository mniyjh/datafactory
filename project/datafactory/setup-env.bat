@echo off
chcp 65001 >nul
title DataFactory 环境配置

echo.
echo  ========================================
echo    DataFactory 环境变量一键配置
echo  ========================================
echo.

:: ── SMTP 邮件 ──
set /p SMTP_USER="QQ邮箱地址 (如 1106935659@qq.com): "
set /p SMTP_PASS="QQ邮箱SMTP授权码 (16位): "

:: ── MySQL ──
set /p DB_USER="MySQL 用户名 (默认 root): " || set DB_USER=root
set /p DB_PASS="MySQL 密码 (默认 123456): " || set DB_PASS=123456

:: ── 写入用户环境变量
echo.
echo  正在设置环境变量...

setx SMTP_USERNAME "%SMTP_USER%" >nul
setx SMTP_PASSWORD "%SMTP_PASS%" >nul
setx DB_USERNAME "%DB_USER%" >nul
setx DB_PASSWORD "%DB_PASS%" >nul

:: ── 当前会话生效 ──
set SMTP_USERNAME=%SMTP_USER%
set SMTP_PASSWORD=%SMTP_PASS%

echo.
echo  ========================================
echo   配置完成！
echo  ========================================
echo.
echo  SMTP_USERNAME = %SMTP_USERNAME%
echo  DB_USERNAME   = %DB_USERNAME%
echo.
echo  (密码不显示，已通过 setx 写入系统)
echo.
echo  重启 IDEA 后生效，或手动执行:
echo    set SMTP_USERNAME=%SMTP_USERNAME%
echo    set SMTP_PASSWORD=你的授权码
echo.
pause
