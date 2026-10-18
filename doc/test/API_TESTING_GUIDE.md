# 檔案傳輸模組 API 測試手冊與測試資料指南 (API Testing Guide)

本文件提供完整的測試資料、Request Payload、cURL 指令以及預期回應結果，專供搭配 `docker-compose` 所建立的外部模擬依賴環境（SFTP、FTP、FTPS、MSSQL 與 FileBrowser Web UI）進行端到端 API 測試。

> [!IMPORTANT]
> **API 全面純化與後端憑證集中管理 (Backend Managed Credentials)**：  
> 本模組遵循企業級安全規範與 Clean Architecture 防腐層設計，**所有伺服器主機 (host)、通訊埠 (port)、協定 (protocol)、帳號與機密密碼/私鑰皆已移入後端資料庫集中管理**（儲存時採用 AES-GCM 高強度加密落盤）。  
> 呼叫檔案傳輸 API (`/api/transmission/*`) 時，**完全不需要在 Request Body 傳入任何帳號或密碼**，僅需傳入伺服器憑證別名或識別碼 (`credentialId`) 以及目標路徑，大幅精簡 API 結構並防止機密外洩！

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
應用程式啟動於通訊埠 `8080`，啟動時會自動透過 `data.sql` 初始化預設伺服器測試憑證：
- **`mock-sftp`**：SFTP 協定，目標 `127.0.0.1:2222`，帳密 `tester / password`
- **`mock-ftp`**：FTP 協定，目標 `127.0.0.1:2121`，帳密 `tester / password`
- **`mock-ftps`**：FTPS 協定，目標 `127.0.0.1:2122`，帳密 `tester / password`

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

#### 【TC-01】SFTP 同步上傳檔案 (透過後端 mock-sftp 憑證)
* **測試目標**：指定憑證 ID `mock-sftp`，將本機檔案同步上傳至 SFTP 伺服器的 `/upload` 目錄。
* **端點**：`POST /api/transmission/upload`
* **Swagger / Postman Request Body**（極簡 4 欄位，無任何帳密！）：
```json
{
  "credentialId": "mock-sftp",
  "localFilePath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/upload-demo.txt",
  "remotePath": "/upload",
  "filename": "sftp-uploaded-demo.txt"
}
```
* **PowerShell 測試指令**：
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/transmission/upload" -Method Post -ContentType "application/json" -Body (@{
    credentialId = "mock-sftp"
    localFilePath = "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/upload-demo.txt"
    remotePath = "/upload"
    filename = "sftp-uploaded-demo.txt"
} | ConvertTo-Json)
```
* **預期結果 (HTTP 200 OK)**：
```json
{
  "transmissionId": "隨機生成的 UUID",
  "protocol": "SFTP",
  "status": "COMPLETED",
  "sourcePath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/upload-demo.txt",
  "targetPath": "/upload",
  "fileName": "sftp-uploaded-demo.txt",
  "fileSizeBytes": 139,
  "durationMillis": 120,
  "errorMessage": null
}
```
* **Web UI 驗證**：打開 [http://localhost:8088](http://localhost:8088)，進入 `sftp/` 資料夾，即可看到 `sftp-uploaded-demo.txt` 檔案，點擊可直接線上預覽內容。

---

#### 【TC-02】SFTP 非同步上傳與狀態輪詢
* **測試目標**：將上傳長任務提交至背景執行緒池執行，立即回傳 HTTP 202 Accepted，並透過 `trackingId` 查詢進度。
* **步驟 1 - 提交非同步任務**：`POST /api/transmission/upload/async`
```json
{
  "credentialId": "mock-sftp",
  "localFilePath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/upload-demo.txt",
  "remotePath": "/upload",
  "filename": "sftp-async-demo.txt"
}
```
* **預期結果 (HTTP 202 Accepted)**：
```json
{
  "trackingId": "550e8400-e29b-41d4-a716-446655440000",
  "protocol": "SFTP",
  "status": "IN_PROGRESS",
  "message": "檔案上傳任務已提交至背景執行緒執行",
  "submittedAt": "2026-10-18T..."
}
```
* **步驟 2 - 輪詢狀態查詢**：`GET /api/transmission/status/{trackingId}`
  * 例如：`http://localhost:8080/api/transmission/status/550e8400-e29b-41d4-a716-446655440000`
  * **預期結果 (HTTP 200 OK)**：`status` 由 `IN_PROGRESS` 轉變為 `COMPLETED`。

---

#### 【TC-03】FTP (明文協定) 同步上傳檔案
* **測試目標**：指定憑證 ID `mock-ftp`，驗證透過 FTP 協定進行檔案上傳。
* **端點**：`POST /api/transmission/upload`
* **Swagger / Postman Request Body**：
```json
{
  "credentialId": "mock-ftp",
  "localFilePath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/upload-demo.txt",
  "remotePath": "/",
  "filename": "ftp-uploaded-demo.txt"
}
```
* **預期結果 (HTTP 200 OK)**：回傳 `status: "COMPLETED"`。
* **Web UI 驗證**：FileBrowser 的 `ftp/` 目錄下立即出現 `ftp-uploaded-demo.txt`。

---

#### 【TC-04】FTPS (TLS 加密) 同步上傳檔案
* **測試目標**：指定憑證 ID `mock-ftps`，驗證 FTPS Explicit TLS 模式安全通道傳輸。
* **端點**：`POST /api/transmission/upload`
* **Swagger / Postman Request Body**：
```json
{
  "credentialId": "mock-ftps",
  "localFilePath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/upload-demo.txt",
  "remotePath": "/",
  "filename": "ftps-uploaded-demo.txt"
}
```
* **預期結果 (HTTP 200 OK)**：回傳 `status: "COMPLETED"`。
* **Web UI 驗證**：FileBrowser 的 `ftps/` 目錄下出現 `ftps-uploaded-demo.txt`。

---

#### 【TC-04B】Multipart 表單檔案上傳 (Zero Disk I/O 記憶體直傳)
* **測試目標**：透過 HTTP Multipart 表單直接上傳二進位串流，後端零磁碟落盤，依 `credentialId` 直傳遠端。
* **端點**：`POST /api/transmission/upload/file`
* **請求格式 (Content-Type)**：`multipart/form-data`
* **Swagger UI / Postman 測試操作說明**：
  1. 開啟 Swagger UI ([http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html))，展開 `POST /api/transmission/upload/file`。
  2. 點擊 **Try it out**。
  3. `file` 欄位：選取本機檔案（例如 `test-data/local/upload-demo.txt`）。
  4. `resource` 欄位 (JSON 部件)：
     ```json
     {
       "credentialId": "mock-sftp",
       "remotePath": "/upload",
       "filename": "sftp-multipart-uploaded.txt"
     }
     ```
  5. 點擊 **Execute**。
* **cURL 測試指令**：
```powershell
curl.exe -X POST "http://localhost:8080/api/transmission/upload/file" `
  -F "file=@D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/upload-demo.txt" `
  -F "credentialId=mock-sftp" `
  -F "remotePath=/upload" `
  -F "filename=sftp-multipart-uploaded.txt"
```
* **預期結果 (HTTP 200 OK)**：
```json
{
  "transmissionId": "隨機生成的 UUID",
  "protocol": "SFTP",
  "status": "COMPLETED",
  "sourcePath": "<memory>",
  "targetPath": "/upload",
  "fileName": "sftp-multipart-uploaded.txt",
  "fileSizeBytes": 139,
  "durationMillis": 75,
  "errorMessage": null
}
```

---

### 群組 B：檔案下載測試 (Download API)

---

#### 【TC-05】SFTP 同步下載檔案至本機
* **測試目標**：指定憑證 ID `mock-sftp`，將遠端檔案下載至本機 `test-data/download/` 目錄。
* **端點**：`POST /api/transmission/download`
* **Swagger / Postman Request Body**：
```json
{
  "credentialId": "mock-sftp",
  "remoteFilePath": "/upload/sftp-uploaded-demo.txt",
  "savedLocalPath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/download/sftp-downloaded-demo.txt"
}
```
* **預期結果 (HTTP 200 OK)**：
```json
{
  "transmissionId": "隨機生成的 UUID",
  "protocol": "SFTP",
  "status": "COMPLETED",
  "sourcePath": "/upload/sftp-uploaded-demo.txt",
  "targetPath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/download/sftp-downloaded-demo.txt",
  "fileName": "sftp-uploaded-demo.txt",
  "fileSizeBytes": 139,
  "durationMillis": 90,
  "errorMessage": null
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
  "credentialId": "mock-ftp",
  "remoteFilePath": "/ftp-uploaded-demo.txt",
  "savedLocalPath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/download/ftp-downloaded-demo.txt"
}
```
* **預期結果 (HTTP 202 Accepted)**：立即回傳 `trackingId`，背景完成下載後儲存至本機目標路徑。

---

### 群組 C：目錄雙向同步測試 (Directory Sync API)

---

#### 【TC-07】本機目錄同步至遠端 SFTP (LOCAL_TO_REMOTE)
* **測試目標**：將 `test-data/local/sync-source` 整體目錄同步傳輸至遠端 `/upload/sync-dest`。
* **端點**：`POST /api/transmission/sync`
* **Swagger / Postman Request Body**：
```json
{
  "credentialId": "mock-sftp",
  "localDir": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/sync-source",
  "remoteDir": "/upload/sync-dest",
  "direction": "LOCAL_TO_REMOTE",
  "deleteRemoteExtras": false
}
```
* **預期結果 (HTTP 200 OK)**：
```json
{
  "transmissionId": "隨機生成的 UUID",
  "protocol": "SFTP",
  "status": "COMPLETED",
  "direction": "LOCAL_TO_REMOTE",
  "localDir": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/sync-source",
  "remoteDir": "/upload/sync-dest",
  "filesTransferredCount": 2,
  "filesDeletedCount": 0,
  "durationMillis": 240,
  "errorMessage": null
}
```
* **Web UI 驗證**：進入 FileBrowser 的 `sftp/sync-dest/` 目錄，確認檔案均已完整同步。

---

#### 【TC-08】非同步目錄同步長任務 (Mode 1)
* **測試目標**：提交非同步目錄同步任務至背景執行。
* **端點**：`POST /api/transmission/sync/async`
* **Swagger / Postman Request Body**：
```json
{
  "credentialId": "mock-ftp",
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

#### 【TC-09】憑證識別碼不存在 (預期 400 Bad Request)
* **測試目標**：驗證當傳入未註冊之 `credentialId` 時，系統回傳友善錯誤。
* **端點**：`POST /api/transmission/upload`
* **Request Body**：
```json
{
  "credentialId": "non-existing-credential",
  "localFilePath": "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/upload-demo.txt",
  "remotePath": "/upload",
  "filename": "should-fail.txt"
}
```
* **預期結果 (HTTP 400 Bad Request)**：
```json
{
  "timestamp": "2026-10-18T...",
  "status": 400,
  "error": "Bad Request",
  "message": "查無指定的伺服器憑證 [ID/別名: non-existing-credential]",
  "path": "/api/transmission/upload",
  "exception": "IllegalArgumentException"
}
```

---

#### 【TC-10】來源本機檔案不存在
* **測試目標**：驗證當指定的本機來源檔案路徑不存在時的錯誤回應。
* **端點**：`POST /api/transmission/upload`
* **Request Body**：
```json
{
  "credentialId": "mock-sftp",
  "localFilePath": "D:/not-exists/non-existent-file.txt",
  "remotePath": "/upload",
  "filename": "fail.txt"
}
```
* **預期結果 (HTTP 500 Internal Server Error)**：回傳錯誤描述包含「本機上傳來源檔案不存在或非一般檔案」。

---

#### 【TC-11】查詢不存在的 trackingId
* **測試目標**：驗證查詢未知 trackingId 時回傳 404 Not Found。
* **端點**：`GET /api/transmission/status/non-existent-tracking-id-999`
* **預期結果 (HTTP 404 Not Found)**：回傳 404 狀態碼。

---

#### 【TC-12】Multipart 表單檔案為空 (預期 400 Bad Request)
* **測試目標**：驗證當上傳檔案為空二進位時回傳 400。
* **端點**：`POST /api/transmission/upload/file`
* **預期結果 (HTTP 400 Bad Request)**：`message: "上傳檔案不能為空"`。

---

### 群組 E：後端伺服器憑證維護 API (Server Credentials API)

---

#### 【TC-13】註冊全新伺服器連線憑證 (密碼/私鑰自動 AES-GCM 加密落盤)
* **測試目標**：透過獨立的憑證管理 API 註冊新主機，密碼由後端自動加密入庫。
* **端點**：`POST /api/credentials`
* **Request Body**：
```json
{
  "alias": "my-prod-sftp",
  "host": "127.0.0.1",
  "port": 2222,
  "protocol": "SFTP",
  "username": "tester",
  "password": "password",
  "description": "生產環境連線憑證"
}
```
* **預期結果 (HTTP 201 Created)**：回傳脫敏後的憑證資訊 (`maskedPassword: "******"`)。

---

#### 【TC-14】依別名 (Alias) 或 ID 查詢憑證
* **端點**：`GET /api/credentials/alias/mock-sftp`
* **預期結果 (HTTP 200 OK)**：回傳 `mock-sftp` 憑證的完整主機資訊，密碼呈現 `******` 脫敏遮罩。

---

## 4. 自動化測試腳本 (PowerShell Automated Runner)

專案提供了一鍵執行的自動化測試腳本，自動涵蓋 TC-01 ~ TC-15 端對端驗證：

```powershell
powershell -ExecutionPolicy Bypass -File "./test-data/run-all-tests.ps1"
```
（該腳本將自動按順序執行並在終端機輸出彩色執行結果報告）
