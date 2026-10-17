# 檔案傳輸模組 API 測試手冊與測試資料指南 (API Testing Guide)

本文件提供完整的測試資料、Request Payload、cURL 指令以及預期回應結果，專供搭配 `docker-compose` 所建立的外部模擬依賴環境（SFTP、FTP、FTPS、MSSQL 與 FileBrowser Web UI）進行端到端 API 測試。

---

## 1. 測試環境前置準備 (Prerequisites)

### 步驟 1：一鍵啟動 Docker 外部依賴
請於專案根目錄開啟終端機，進入 `docker-compose` 目錄啟動所有容器：
```powershell
cd docker-compose
docker compose up -d
```
> 確認各容器狀態：`docker compose ps`（確保 `mock-sftp-server`、`mock-ftp-server`、`mock-ftps-server`、`mock-file-manager-ui`、`mock-mssql-server` 均正常運行）。

### 步驟 2：啟動 Spring Boot 應用程式
回到專案根目錄執行：
```powershell
.\mvnw.cmd spring-boot:run
```
應用程式啟動於通訊埠 `8080`。

### 步驟 3：開啟驗證介面
* **Swagger API 測試介面**：[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* **FileBrowser 視覺化檔案管理介面**：[http://localhost:8088](http://localhost:8088)（免登入直接檢視遠端磁碟檔案變化）

---

## 2. 測試資料目錄與檔案清單 (Test Data Assets)

專案內已於 `test-data/` 準備好真實測試檔案，路徑結構如下：

```
file-transmission-module/
└── test-data/
    ├── local/
    │   ├── upload-demo.txt            # 單檔上傳測試檔案
    │   └── sync-source/               # 目錄同步測試來源資料夾
    │       ├── report-2026.txt
    │       └── data.json
    └── download/                      # 下載測試之目標儲存目錄
```

> [!NOTE]
> 在 Windows 環境的 JSON 請求中，檔案路徑推薦使用正斜線 `/`（如 `D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/...`），Java 能跨平台自動相容解析。

---

## 3. 測試案例清單與測試資料 (Test Cases & Payloads)

### 群組 A：檔案上傳測試 (Upload API)

---

#### 【TC-01】SFTP 同步上傳檔案
* **測試目標**：驗證透過 SFTP (SSH Port 2222) 將本機檔案同步上傳至遠端 `/upload` 目錄。
* **端點**：`POST /api/transmission/upload`
* **Swagger / Postman Request Body**：
```json
{
  "host": "127.0.0.1",
  "port": 2222,
  "username": "tester",
  "password": "password",
  "protocol": "SFTP",
  "timeoutMs": 10000,
  "passiveMode": true,
  "localFilePath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/upload-demo.txt",
  "remotePath": "/upload",
  "filename": "sftp-uploaded-demo.txt"
}
```
* **PowerShell cURL 測試指令**：
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/transmission/upload" -Method Post -ContentType "application/json" -Body (@{
    host = "127.0.0.1"
    port = 2222
    username = "tester"
    password = "password"
    protocol = "SFTP"
    timeoutMs = 10000
    passiveMode = $true
    localFilePath = "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/upload-demo.txt"
    remotePath = "/upload"
    filename = "sftp-uploaded-demo.txt"
} | ConvertTo-Json)
```
* **預期結果 (HTTP 200 OK)**：
```json
{
  "trackingId": "隨機生成的 UUID",
  "sourcePath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/upload-demo.txt",
  "targetPath": "/upload",
  "fileName": "sftp-uploaded-demo.txt",
  "fileSizeBytes": 139,
  "status": "COMPLETED",
  "errorMessage": null,
  "completedAt": "2026-10-17T..."
}
```
* **Web UI 驗證**：打開 [http://localhost:8088](http://localhost:8088)，進入 `sftp/` 資料夾，即可看到 `sftp-uploaded-demo.txt` 檔案，點擊可直接線上預覽內容。

---

#### 【TC-02】SFTP 非同步上傳與狀態輪詢
* **測試目標**：驗證上傳任務提交至背景執行緒池執行，立即回傳 HTTP 202 Accepted，並透過 trackingId 查詢進度。
* **步驟 1 - 提交非同步任務**：`POST /api/transmission/upload/async`
```json
{
  "host": "127.0.0.1",
  "port": 2222,
  "username": "tester",
  "password": "password",
  "protocol": "SFTP",
  "timeoutMs": 10000,
  "passiveMode": true,
  "localFilePath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/upload-demo.txt",
  "remotePath": "/upload",
  "filename": "sftp-async-demo.txt"
}
```
* **預期結果 (HTTP 202 Accepted)**：
```json
{
  "trackingId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "IN_PROGRESS",
  "message": "傳輸作業已成功提交至背景排程執行緒池",
  "submittedAt": "2026-10-17T..."
}
```
* **步驟 2 - 輪詢狀態查詢**：`GET /api/transmission/status/{trackingId}`
  * 例如：`http://localhost:8080/api/transmission/status/550e8400-e29b-41d4-a716-446655440000`
  * **預期結果 (HTTP 200 OK)**：`status` 由 `IN_PROGRESS` 轉變為 `COMPLETED`。

---

#### 【TC-03】FTP (明文協定) 同步上傳檔案
* **測試目標**：驗證透過傳統 FTP 協定 (Port 2121 + PASV Mode) 進行檔案上傳。
* **端點**：`POST /api/transmission/upload`
* **Swagger / Postman Request Body**：
```json
{
  "host": "127.0.0.1",
  "port": 2121,
  "username": "tester",
  "password": "password",
  "protocol": "FTP",
  "timeoutMs": 10000,
  "passiveMode": true,
  "localFilePath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/upload-demo.txt",
  "remotePath": "/",
  "filename": "ftp-uploaded-demo.txt"
}
```
* **預期結果 (HTTP 200 OK)**：回傳 `status: "COMPLETED"`。
* **Web UI 驗證**：FileBrowser 的 `ftp/` 目錄下立即出現 `ftp-uploaded-demo.txt`。

---

#### 【TC-04】FTPS (TLS 加密) 同步上傳檔案
* **測試目標**：驗證 FTPS Explicit TLS 模式 (Port 2122 + PROT P 加密通道 + 自簽憑證信任機制) 檔案上傳。
* **端點**：`POST /api/transmission/upload`
* **Swagger / Postman Request Body**：
```json
{
  "host": "127.0.0.1",
  "port": 2122,
  "username": "tester",
  "password": "password",
  "protocol": "FTPS",
  "timeoutMs": 10000,
  "passiveMode": true,
  "localFilePath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/upload-demo.txt",
  "remotePath": "/",
  "filename": "ftps-uploaded-demo.txt"
}
```
* **預期結果 (HTTP 200 OK)**：回傳 `status: "COMPLETED"`。
* **Web UI 驗證**：FileBrowser 的 `ftps/` 目錄下出現 `ftps-uploaded-demo.txt`。

---

### 群組 B：檔案下載測試 (Download API)

---

#### 【TC-05】SFTP 同步下載檔案至本機
* **測試目標**：將遠端 SFTP 伺服器上的檔案下載至本機 `test-data/download/` 目錄。
* **端點**：`POST /api/transmission/download`
* **Swagger / Postman Request Body**：
```json
{
  "host": "127.0.0.1",
  "port": 2222,
  "username": "tester",
  "password": "password",
  "protocol": "SFTP",
  "timeoutMs": 10000,
  "passiveMode": true,
  "remoteFilePath": "/upload/sftp-uploaded-demo.txt",
  "savedPath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/download/sftp-downloaded-demo.txt"
}
```
* **預期結果 (HTTP 200 OK)**：
```json
{
  "trackingId": "隨機生成的 UUID",
  "sourcePath": "/upload/sftp-uploaded-demo.txt",
  "targetPath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/download/sftp-downloaded-demo.txt",
  "fileName": "sftp-downloaded-demo.txt",
  "fileSizeBytes": 139,
  "status": "COMPLETED",
  "errorMessage": null,
  "completedAt": "2026-10-17T..."
}
```
* **本機驗證**：檢查 `test-data/download/sftp-downloaded-demo.txt` 是否成功生成且內容一致。

---

#### 【TC-06】FTP 非同步下載檔案
* **測試目標**：非同步將 FTP 伺服器上的檔案下載至本機。
* **端點**：`POST /api/transmission/download/async`
* **Swagger / Postman Request Body**：
```json
{
  "host": "127.0.0.1",
  "port": 2121,
  "username": "tester",
  "password": "password",
  "protocol": "FTP",
  "timeoutMs": 10000,
  "passiveMode": true,
  "remoteFilePath": "/ftp-uploaded-demo.txt",
  "savedPath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/download/ftp-downloaded-demo.txt"
}
```
* **預期結果 (HTTP 202 Accepted)**：立即回傳 `trackingId`，背景完成下載後儲存至本機目標路徑。

---

### 群組 C：目錄雙向同步測試 (Directory Sync API)

---

#### 【TC-07】本機目錄同步至遠端 SFTP (LOCAL_TO_REMOTE)
* **測試目標**：驗證將 `test-data/local/sync-source` 整體目錄同步傳輸至遠端 `/upload/sync-dest`。
* **端點**：`POST /api/transmission/sync`
* **Swagger / Postman Request Body**：
```json
{
  "host": "127.0.0.1",
  "port": 2222,
  "username": "tester",
  "password": "password",
  "protocol": "SFTP",
  "timeoutMs": 10000,
  "passiveMode": true,
  "localDir": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/sync-source",
  "remoteDir": "/upload/sync-dest",
  "direction": "LOCAL_TO_REMOTE",
  "deleteRemoteExtras": false
}
```
* **預期結果 (HTTP 200 OK)**：
```json
{
  "trackingId": "隨機生成的 UUID",
  "direction": "LOCAL_TO_REMOTE",
  "sourcePath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/sync-source",
  "targetPath": "/upload/sync-dest",
  "transferredCount": 2,
  "deletedCount": 0,
  "status": "COMPLETED",
  "completedAt": "2026-10-17T..."
}
```
* **Web UI 驗證**：進入 FileBrowser 的 `sftp/sync-dest/` 目錄，確認 `report-2026.txt` 與 `data.json` 均已完整同步。

---

#### 【TC-08】非同步目錄同步長任務 (Mode 1)
* **測試目標**：提交非同步目錄同步任務，測試後台排程執行緒處理能力。
* **端點**：`POST /api/transmission/sync/async`
* **Swagger / Postman Request Body**：
```json
{
  "host": "127.0.0.1",
  "port": 2121,
  "username": "tester",
  "password": "password",
  "protocol": "FTP",
  "timeoutMs": 10000,
  "passiveMode": true,
  "localDir": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/sync-source",
  "remoteDir": "/ftp-sync-folder",
  "direction": "LOCAL_TO_REMOTE",
  "deleteRemoteExtras": false
}
```
* **預期結果 (HTTP 202 Accepted)**：回傳 `trackingId`，背景自動執行檔案傳輸。

---

### 群組 D：異常分支與錯誤處理測試 (Error Handling)

---

#### 【TC-09】遠端伺服器連線認證失敗 (密碼錯誤)
* **測試目標**：驗證輸入錯誤密碼時，`GlobalExceptionHandler` 是否正確轉譯為統一錯誤格式與 HTTP 502 狀態碼。
* **端點**：`POST /api/transmission/upload`
* **Request Body**（密碼故意給 `wrong_password`）：
```json
{
  "host": "127.0.0.1",
  "port": 2222,
  "username": "tester",
  "password": "wrong_password",
  "protocol": "SFTP",
  "timeoutMs": 5000,
  "passiveMode": true,
  "localFilePath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/upload-demo.txt",
  "remotePath": "/upload",
  "filename": "should-fail.txt"
}
```
* **預期結果 (HTTP 502 Bad Gateway)**：
```json
{
  "status": 502,
  "error": "Bad Gateway",
  "message": "遠端檔案傳輸伺服器連線或身分驗證失敗: ...",
  "timestamp": "2026-10-17T..."
}
```

---

#### 【TC-10】來源本機檔案不存在
* **測試目標**：驗證當指定的本機來源檔案路徑不存在時的錯誤回應。
* **端點**：`POST /api/transmission/upload`
* **Request Body**：
```json
{
  "host": "127.0.0.1",
  "port": 2222,
  "username": "tester",
  "password": "password",
  "protocol": "SFTP",
  "timeoutMs": 5000,
  "passiveMode": true,
  "localFilePath": "D:/not-exists/non-existent-file.txt",
  "remotePath": "/upload",
  "filename": "fail.txt"
}
```
* **預期結果 (HTTP 500 Internal Server Error)**：
```json
{
  "status": 500,
  "error": "Internal Server Error",
  "message": "檔案傳輸作業執行失敗: 本機上傳來源檔案不存在或非一般檔案: D:/not-exists/non-existent-file.txt",
  "timestamp": "2026-10-17T..."
}
```

---

#### 【TC-11】查詢不存在的 trackingId
* **測試目標**：驗證查詢未知 trackingId 時回傳 404 Not Found。
* **端點**：`GET /api/transmission/status/non-existent-tracking-id-999`
* **預期結果 (HTTP 404 Not Found)**：回傳空 Body 與 404 狀態碼。

---

## 4. 自動化測試腳本 (PowerShell Automated Runner)

若您想一鍵依序執行所有 API 測試案例並驗證結果，可於 PowerShell 執行專案提供的自動測試腳本：

```powershell
powershell -ExecutionPolicy Bypass -File "./test-data/run-all-tests.ps1"
```
（該腳本將自動按順序執行 TC-01 ~ TC-11 並在終端機輸出彩色執行報告）
