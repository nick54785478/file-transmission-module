# ==============================================================================
# 檔案傳輸模組 (File Transmission Module) API 自動化端對端測試腳本
# ==============================================================================
$ErrorActionPreference = "Continue"
$baseUrl = "http://localhost:8080"
$baseDir = "D:/桌面/ModuleWorkSpace/file-transmission-module"
$uploadFile = "$baseDir/test-data/local/upload-demo.txt"
$syncFolder = "$baseDir/test-data/local/sync-source"
$downloadDir = "$baseDir/test-data/download"

function Write-Step($title) {
    Write-Host "`n==================================================" -ForegroundColor Cyan
    Write-Host ">>> $title" -ForegroundColor Yellow
    Write-Host "==================================================" -ForegroundColor Cyan
}

function Test-ApiHealth {
    try {
        $resp = Invoke-WebRequest -Uri "$baseUrl/swagger-ui.html" -Method Head -TimeoutSec 3 -ErrorAction Stop
        return $true
    } catch {
        return $false
    }
}

Write-Host "`n檢測 Spring Boot 應用程式狀態..." -ForegroundColor Gray
if (-not (Test-ApiHealth)) {
    Write-Host "[警告] 無法連線至 $baseUrl，請先確認 Spring Boot 服務已啟動 (.\\mvnw.cmd spring-boot:run)" -ForegroundColor Red
    Write-Host "腳本將繼續嘗試呼叫 API...`n" -ForegroundColor DarkGray
}

# ------------------------------------------------------------------------------
# TC-01: SFTP 同步上傳
# ------------------------------------------------------------------------------
Write-Step "【TC-01】SFTP 同步上傳檔案測試"
$tc01Body = @{
    host = "127.0.0.1"
    port = 2222
    username = "tester"
    password = "password"
    protocol = "SFTP"
    timeoutMs = 10000
    passiveMode = $true
    localFilePath = $uploadFile
    remotePath = "/upload"
    filename = "sftp-uploaded-auto.txt"
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/transmission/upload" -Method Post -ContentType "application/json" -Body $tc01Body
    Write-Host " -> 上傳成功! Status: $($res.status), TrackingId: $($res.trackingId)" -ForegroundColor Green
} catch {
    Write-Host " -> 呼叫失敗: $($_.Exception.Message)" -ForegroundColor Red
}

# ------------------------------------------------------------------------------
# TC-02: SFTP 非同步上傳與輪詢狀態
# ------------------------------------------------------------------------------
Write-Step "【TC-02】SFTP 非同步上傳與狀態輪詢"
$tc02Body = @{
    host = "127.0.0.1"
    port = 2222
    username = "tester"
    password = "password"
    protocol = "SFTP"
    timeoutMs = 10000
    passiveMode = $true
    localFilePath = $uploadFile
    remotePath = "/upload"
    filename = "sftp-async-auto.txt"
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/transmission/upload/async" -Method Post -ContentType "application/json" -Body $tc02Body
    $trackingId = $res.trackingId
    Write-Host " -> 提交成功! Status: $($res.status), TrackingId: $trackingId" -ForegroundColor Green

    Start-Sleep -Seconds 2
    $statusRes = Invoke-RestMethod -Uri "$baseUrl/api/transmission/status/$trackingId" -Method Get
    Write-Host " -> 狀態查詢成功! Status: $($statusRes.status), File: $($statusRes.fileName)" -ForegroundColor Green
} catch {
    Write-Host " -> 呼叫失敗: $($_.Exception.Message)" -ForegroundColor Red
}

# ------------------------------------------------------------------------------
# TC-03: FTP 同步上傳
# ------------------------------------------------------------------------------
Write-Step "【TC-03】FTP 同步上傳檔案測試"
$tc03Body = @{
    host = "127.0.0.1"
    port = 2121
    username = "tester"
    password = "password"
    protocol = "FTP"
    timeoutMs = 10000
    passiveMode = $true
    localFilePath = $uploadFile
    remotePath = "/"
    filename = "ftp-uploaded-auto.txt"
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/transmission/upload" -Method Post -ContentType "application/json" -Body $tc03Body
    Write-Host " -> 上傳成功! Status: $($res.status), File: $($res.fileName)" -ForegroundColor Green
} catch {
    Write-Host " -> 呼叫失敗: $($_.Exception.Message)" -ForegroundColor Red
}

# ------------------------------------------------------------------------------
# TC-04: FTPS (TLS) 同步上傳
# ------------------------------------------------------------------------------
Write-Step "【TC-04】FTPS (TLS 加密) 同步上傳檔案測試"
$tc04Body = @{
    host = "127.0.0.1"
    port = 2122
    username = "tester"
    password = "password"
    protocol = "FTPS"
    timeoutMs = 10000
    passiveMode = $true
    localFilePath = $uploadFile
    remotePath = "/"
    filename = "ftps-uploaded-auto.txt"
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/transmission/upload" -Method Post -ContentType "application/json" -Body $tc04Body
    Write-Host " -> 上傳成功! Status: $($res.status), File: $($res.fileName)" -ForegroundColor Green
} catch {
    Write-Host " -> 呼叫失敗: $($_.Exception.Message)" -ForegroundColor Red
}

# ------------------------------------------------------------------------------
# TC-05: SFTP 同步下載
# ------------------------------------------------------------------------------
Write-Step "【TC-05】SFTP 同步下載檔案測試"
$tc05Body = @{
    host = "127.0.0.1"
    port = 2222
    username = "tester"
    password = "password"
    protocol = "SFTP"
    timeoutMs = 10000
    passiveMode = $true
    remoteFilePath = "/upload/sftp-uploaded-auto.txt"
    savedPath = "$downloadDir/sftp-downloaded-auto.txt"
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/transmission/download" -Method Post -ContentType "application/json" -Body $tc05Body
    Write-Host " -> 下載成功! Status: $($res.status), Saved: $($res.targetPath)" -ForegroundColor Green
} catch {
    Write-Host " -> 呼叫失敗: $($_.Exception.Message)" -ForegroundColor Red
}

# ------------------------------------------------------------------------------
# TC-07: SFTP 目錄同步 (Local to Remote)
# ------------------------------------------------------------------------------
Write-Step "【TC-07】SFTP 目錄同步 (LOCAL_TO_REMOTE)"
$tc07Body = @{
    host = "127.0.0.1"
    port = 2222
    username = "tester"
    password = "password"
    protocol = "SFTP"
    timeoutMs = 10000
    passiveMode = $true
    localDir = $syncFolder
    remoteDir = "/upload/sync-auto"
    direction = "LOCAL_TO_REMOTE"
    deleteRemoteExtras = $false
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/transmission/sync" -Method Post -ContentType "application/json" -Body $tc07Body
    Write-Host " -> 目錄同步成功! Transferred: $($res.transferredCount), Status: $($res.status)" -ForegroundColor Green
} catch {
    Write-Host " -> 呼叫失敗: $($_.Exception.Message)" -ForegroundColor Red
}

# ------------------------------------------------------------------------------
# TC-09: 異常情境 - 密碼錯誤 (預期 502 Bad Gateway)
# ------------------------------------------------------------------------------
Write-Step "【TC-09】異常情境測試：密碼錯誤 (預期 502)"
$tc09Body = @{
    host = "127.0.0.1"
    port = 2222
    username = "tester"
    password = "wrong_password_123"
    protocol = "SFTP"
    timeoutMs = 5000
    passiveMode = $true
    localFilePath = $uploadFile
    remotePath = "/upload"
    filename = "should-fail.txt"
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/transmission/upload" -Method Post -ContentType "application/json" -Body $tc09Body
    Write-Host " -> 錯誤: 未如預期拋出異常!" -ForegroundColor Red
} catch {
    $statusCode = [int]$_.Exception.Response.StatusCode
    Write-Host " -> 捕獲預期異常! HTTP 狀態碼: $statusCode" -ForegroundColor Green
}

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "測試執行完成！請至 FileBrowser (http://localhost:8088) 檢視檔案。" -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Cyan
