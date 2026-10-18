# ==============================================================================
# 檔案傳輸模組 (File Transmission Module) API 自動化端對端測試腳本
# ==============================================================================
# 說明：
# 傳輸 API 相關的連線設定 (主機、通訊埠、帳號、密碼等) 已移入後端集中管理。
# 所有傳輸請求僅需提供 credentialId (如 mock-sftp, mock-ftp, mock-ftps) 與傳輸路徑。
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
# TC-01: SFTP 同步上傳 (透過後端 mock-sftp 憑證)
# ------------------------------------------------------------------------------
Write-Step "【TC-01】SFTP 同步上傳檔案測試 (透過 credentialId: mock-sftp)"
$tc01Body = @{
    credentialId = "mock-sftp"
    localFilePath = $uploadFile
    remotePath = "/upload"
    filename = "sftp-uploaded-auto.txt"
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/transmission/upload" -Method Post -ContentType "application/json" -Body $tc01Body
    Write-Host " -> 上傳成功! Status: $($res.status), TrackingId: $($res.transmissionId)" -ForegroundColor Green
} catch {
    Write-Host " -> 呼叫失敗: $($_.Exception.Message)" -ForegroundColor Red
}

# ------------------------------------------------------------------------------
# TC-02: SFTP 非同步上傳與輪詢狀態
# ------------------------------------------------------------------------------
Write-Step "【TC-02】SFTP 非同步上傳與狀態輪詢"
$tc02Body = @{
    credentialId = "mock-sftp"
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
# TC-03: FTP 同步上傳 (透過後端 mock-ftp 憑證)
# ------------------------------------------------------------------------------
Write-Step "【TC-03】FTP 同步上傳檔案測試 (透過 credentialId: mock-ftp)"
$tc03Body = @{
    credentialId = "mock-ftp"
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
# TC-04: FTPS (TLS) 同步上傳 (透過後端 mock-ftps 憑證)
# ------------------------------------------------------------------------------
Write-Step "【TC-04】FTPS (TLS 加密) 同步上傳檔案測試 (透過 credentialId: mock-ftps)"
$tc04Body = @{
    credentialId = "mock-ftps"
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
# TC-04B: Multipart 表單上傳 (Zero Disk I/O 記憶體直傳)
# ------------------------------------------------------------------------------
Write-Step "【TC-04B】Multipart 表單檔案上傳測試 (Zero Disk I/O 記憶體直傳)"
try {
    $resJson = curl.exe -s -X POST "$baseUrl/api/transmission/upload/file" `
        -F "file=@$uploadFile" `
        -F "credentialId=mock-sftp" `
        -F "remotePath=/upload" `
        -F "filename=sftp-multipart-auto.txt"
    $res = $resJson | ConvertFrom-Json
    Write-Host " -> 上傳成功! Status: $($res.status), File: $($res.fileName), Source: $($res.sourcePath)" -ForegroundColor Green
} catch {
    Write-Host " -> 呼叫失敗: $($_.Exception.Message)" -ForegroundColor Red
}

# ------------------------------------------------------------------------------
# TC-05: SFTP 同步下載
# ------------------------------------------------------------------------------
Write-Step "【TC-05】SFTP 同步下載檔案測試 (透過 credentialId: mock-sftp)"
$tc05Body = @{
    credentialId = "mock-sftp"
    remoteFilePath = "/upload/sftp-uploaded-auto.txt"
    savedLocalPath = "$downloadDir/sftp-downloaded-auto.txt"
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
    credentialId = "mock-sftp"
    localDir = $syncFolder
    remoteDir = "/upload/sync-auto"
    direction = "LOCAL_TO_REMOTE"
    deleteRemoteExtras = $false
} | ConvertTo-Json

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/transmission/sync" -Method Post -ContentType "application/json" -Body $tc07Body
    Write-Host " -> 目錄同步成功! Transferred: $($res.filesTransferredCount), Status: $($res.status)" -ForegroundColor Green
} catch {
    Write-Host " -> 呼叫失敗: $($_.Exception.Message)" -ForegroundColor Red
}

# ------------------------------------------------------------------------------
# TC-09: 異常情境 - 憑證識別碼不存在 (預期 400 Bad Request)
# ------------------------------------------------------------------------------
Write-Step "【TC-09】異常情境測試：憑證不存在 (預期 400 Bad Request)"
$tc09Body = @{
    credentialId = "non-existing-credential"
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

# ------------------------------------------------------------------------------
# TC-12: 異常情境 - Multipart 上傳檔案為空 (預期 400 Bad Request)
# ------------------------------------------------------------------------------
Write-Step "【TC-12】異常情境測試：Multipart 上傳空檔案 (預期 400)"
try {
    $emptyTemp = "$env:TEMP\empty-upload-test.txt"
    New-Item -Path $emptyTemp -ItemType File -Force | Out-Null
    $resJson = curl.exe -s -w "`n%{http_code}" -X POST "$baseUrl/api/transmission/upload/file" `
        -F "file=@$emptyTemp" `
        -F "credentialId=mock-sftp" `
        -F "remotePath=/upload"
    $lines = $resJson -split "`n"
    $httpCode = $lines[-1].Trim()
    if ($httpCode -eq "400") {
        Write-Host " -> 捕獲預期異常! HTTP 狀態碼: 400 Bad Request" -ForegroundColor Green
    } else {
        Write-Host " -> 回應狀態碼非預期: $httpCode" -ForegroundColor Yellow
    }
} catch {
    Write-Host " -> 執行例外: $($_.Exception.Message)" -ForegroundColor Red
}

# ------------------------------------------------------------------------------
# TC-13: 註冊伺服器傳輸憑證 (AES-GCM 落盤加密)
# ------------------------------------------------------------------------------
Write-Step "【TC-13】註冊伺服器傳輸憑證 (AES-GCM 加密落盤)"
$credBody = @{
    alias = "mock-sftp-auto"
    host = "127.0.0.1"
    port = 2222
    protocol = "SFTP"
    username = "tester"
    password = "password"
    description = "自動化腳本註冊之 SFTP 憑證"
} | ConvertTo-Json

try {
    $resCred = Invoke-RestMethod -Uri "$baseUrl/api/credentials" -Method Post -ContentType "application/json" -Body $credBody
    Write-Host " -> 憑證註冊成功! ID: $($resCred.id), Alias: $($resCred.alias), MaskedPassword: $($resCred.maskedPassword)" -ForegroundColor Green

    # ------------------------------------------------------------------------------
    # TC-15: 透過自訂註冊之憑證別名快速發起檔案上傳
    # ------------------------------------------------------------------------------
    Write-Step "【TC-15】透過自訂 credentialId 發起檔案上傳"
    $uploadByCredBody = @{
        credentialId = "mock-sftp-auto"
        localFilePath = $uploadFile
        remotePath = "/upload"
        filename = "sftp-via-credential-auto.txt"
    } | ConvertTo-Json

    $resUpload = Invoke-RestMethod -Uri "$baseUrl/api/transmission/upload" -Method Post -ContentType "application/json" -Body $uploadByCredBody
    Write-Host " -> 依憑證上傳成功! TrackingId: $($resUpload.transmissionId), Status: $($resUpload.status)" -ForegroundColor Green
} catch {
    Write-Host " -> 憑證操作或上傳失敗: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n==================================================" -ForegroundColor Cyan
Write-Host "測試執行完成！請至 FileBrowser (http://localhost:8088) 檢視檔案。" -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor Cyan
