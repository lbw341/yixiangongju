@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
echo ============================================
echo   一线工具平台 - 数据库一键初始化
echo ============================================
echo 本脚本将创建数据库账号 tooluser / tooldb123
echo 并授权访问 tooldb 库（应用启动时会自动建库建表）
echo.
where mysql >nul 2>nul
if errorlevel 1 (
    echo 未在 PATH 中找到 mysql 命令。
    set /p MYSQLBIN=请输入 mysql.exe 所在目录（例如 C:\Program Files\MySQL\MySQL Server 5.7\bin）: 
    set "PATH=!MYSQLBIN!;%PATH%"
)
set /p ROOTPWD=请输入 MySQL root 密码: 
echo.
echo 正在初始化账号...
mysql -u root -p%ROOTPWD% --default-character-set=utf8mb4 < "%~dp0database\init_user.sql"
if errorlevel 1 (
    echo 初始化失败：请检查 root 密码是否正确、MySQL 服务是否已启动。
    pause
    exit /b 1
)
echo 数据库账号初始化完成！
echo 现在可以双击 启动.bat 运行平台。
pause
