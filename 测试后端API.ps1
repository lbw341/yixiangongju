Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   一线工具平台 - 后端API测试脚本 (PowerShell)" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

$BASE_URL = "http://localhost:5000/api"
$TOKEN = $null
$TOOL_ID = $null
$step = 1

function Test-API {
    param(
        [string]$Method,
        [string]$Url,
        [string]$ContentType = "application/json",
        [string]$Body = $null,
        [bool]$NeedAuth = $false,
        [hashtable]$FormData = $null
    )
    
    global:$step
    Write-Host "[$step] $Method $Url" -ForegroundColor Yellow
    $headers = @{}
    
    if ($FormData) {
        if ($NeedAuth -and $TOKEN) {
            $headers["Authorization"] = "Bearer $TOKEN"
        }
    } else {
        $headers["Content-Type"] = $ContentType
        if ($NeedAuth -and $TOKEN) {
            $headers["Authorization"] = "Bearer $TOKEN"
        }
    }
    
    try {
        if ($FormData) {
            $response = Invoke-RestMethod -Uri $Url -Method $Method -Headers $headers -Form $FormData -ErrorAction Stop
        } elseif ($Body) {
            $response = Invoke-RestMethod -Uri $Url -Method $Method -Headers $headers -Body $Body -ErrorAction Stop
        } else {
            $response = Invoke-RestMethod -Uri $Url -Method $Method -Headers $headers -ErrorAction Stop
        }
        $response | ConvertTo-Json -Depth 3
    } catch {
        Write-Host "错误: $_" -ForegroundColor Red
    }
    $step++
    Write-Host ""
}

function Test-API-With-Result {
    param(
        [string]$Method,
        [string]$Url,
        [string]$ContentType = "application/json",
        [string]$Body = $null,
        [bool]$NeedAuth = $false,
        [hashtable]$FormData = $null
    )
    
    global:$step
    Write-Host "[$step] $Method $Url" -ForegroundColor Yellow
    $headers = @{}
    
    if ($FormData) {
        if ($NeedAuth -and $TOKEN) {
            $headers["Authorization"] = "Bearer $TOKEN"
        }
    } else {
        $headers["Content-Type"] = $ContentType
        if ($NeedAuth -and $TOKEN) {
            $headers["Authorization"] = "Bearer $TOKEN"
        }
    }
    
    try {
        if ($FormData) {
            $response = Invoke-RestMethod -Uri $Url -Method $Method -Headers $headers -Form $FormData -ErrorAction Stop
        } elseif ($Body) {
            $response = Invoke-RestMethod -Uri $Url -Method $Method -Headers $headers -Body $Body -ErrorAction Stop
        } else {
            $response = Invoke-RestMethod -Uri $Url -Method $Method -Headers $headers -ErrorAction Stop
        }
        $response | ConvertTo-Json -Depth 3
        return $response
    } catch {
        Write-Host "错误: $_" -ForegroundColor Red
        return $null
    }
    $step++
    Write-Host ""
}

Write-Host "【第一阶段：用户认证】" -ForegroundColor Green
Write-Host ""

try {
    $registerResult = Test-API-With-Result -Method POST -Url "$BASE_URL/auth/register" -Body '{"username":"testuser","password":"123456","nickname":"测试用户"}'
} catch {
    Write-Host "用户可能已存在，继续登录..." -ForegroundColor Yellow
}

$loginResponse = Test-API-With-Result -Method POST -Url "$BASE_URL/auth/login" -Body '{"username":"testuser","password":"123456"}'
if ($loginResponse -and $loginResponse.token) {
    $TOKEN = $loginResponse.token
    Write-Host "获取到Token: $TOKEN" -ForegroundColor Green
}
Write-Host ""

Test-API -Method GET -Url "$BASE_URL/auth/me" -NeedAuth $true

Write-Host "【第二阶段：工具管理】" -ForegroundColor Green
Write-Host ""

Test-API -Method GET -Url "$BASE_URL/tools"

Test-API -Method GET -Url "$BASE_URL/tools?category=规划&search=测试"

$createResponse = Test-API-With-Result -Method POST -Url "$BASE_URL/tools/create" -FormData @{
    name = "测试工具"
    type = "自动化工具"
    category = "规划"
    description = "这是一个测试工具"
    keywords = "测试,自动化"
    department = "技术部"
    instructions = "使用说明"
} -NeedAuth $true

if ($createResponse -and $createResponse.tool_id) {
    $TOOL_ID = $createResponse.tool_id
    Write-Host "创建工具ID: $TOOL_ID" -ForegroundColor Green
}
Write-Host ""

if ($TOOL_ID) {
    Test-API -Method GET -Url "$BASE_URL/tools/$TOOL_ID"
    
    Test-API -Method PUT -Url "$BASE_URL/tools/$TOOL_ID/update" -Body '{"name":"测试工具-更新","description":"更新后的描述"}' -NeedAuth $true
}

Write-Host "【第三阶段：评价与消息】" -ForegroundColor Green
Write-Host ""

if ($TOOL_ID) {
    Test-API -Method POST -Url "$BASE_URL/reviews" -Body "{`"tool_id`":$TOOL_ID,`"content`":`"这个工具很好用！`"}" -NeedAuth $true
}

Test-API -Method POST -Url "$BASE_URL/messages/create" -Body '{"title":"测试消息","type":"问题反馈","content":"这是一条测试消息"}' -NeedAuth $true

Test-API -Method GET -Url "$BASE_URL/messages" -NeedAuth $true

Write-Host "【第四阶段：统计数据】" -ForegroundColor Green
Write-Host ""

Test-API -Method GET -Url "$BASE_URL/stats/dashboard"

if ($TOOL_ID) {
    Test-API -Method GET -Url "$BASE_URL/stats/tool/$TOOL_ID"
}

Test-API -Method GET -Url "$BASE_URL/stats/recent_usage" -NeedAuth $true

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   测试完成！" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan