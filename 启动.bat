@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
echo ============================================
echo   一线工具平台 - 启动
echo ============================================
where java >nul 2>nul || (echo [错误] 未找到 java，请先安装 JDK 21 并加入 PATH。 & pause & exit /b 1)
where mvn >nul 2>nul || (echo [错误] 未找到 mvn，请先安装 Maven 并加入 PATH。 & pause & exit /b 1)

cd /d "%~dp0backend-java"
echo 首次运行会自动下载依赖并建库建表（需 MySQL 已按说明初始化账号）...
echo 启动成功后请访问: http://localhost:5000
echo.
call mvn spring-boot:run
pause
