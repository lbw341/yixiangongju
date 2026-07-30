@echo off
chcp 65001 >nul
echo ============================================
echo   一线工具平台 - 后端API测试脚本
echo ============================================
echo.

set BASE_URL=http://localhost:5000/api
set TOKEN=

echo [1/6] 测试用户登录...
for /f "delims=" %%a in ('curl.exe -s -X POST %BASE_URL%/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"testuser\",\"password\":\"123456\"}"') do (
  set RESPONSE=%%a
  echo %%a
)

echo.
echo 提取Token...
for /f tokens^=2^ delims^=^" %%t in ('echo %RESPONSE% ^| findstr /i "token"') do (
  set TOKEN=%%t
)
echo 获取到Token: %TOKEN%
echo.

echo [2/6] 测试获取当前用户信息...
curl.exe -s -X GET %BASE_URL%/auth/me ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer %TOKEN%"

echo.
echo.

echo [3/6] 测试获取工具列表...
curl.exe -s -X GET "%BASE_URL%/tools?category=规划"

echo.
echo.

echo [4/6] 测试获取仪表盘统计...
curl.exe -s -X GET %BASE_URL%/stats/dashboard

echo.
echo.

echo [5/6] 测试发送消息...
curl.exe -s -X POST %BASE_URL%/messages/create ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer %TOKEN%" ^
  -d "{\"title\":\"测试消息\",\"type\":\"问题反馈\",\"content\":\"这是一条测试消息\"}"

echo.
echo.

echo [6/6] 测试获取消息列表...
curl.exe -s -X GET %BASE_URL%/messages ^
  -H "Authorization: Bearer %TOKEN%"

echo.
echo.
echo ============================================
echo   测试完成！
echo ============================================
pause