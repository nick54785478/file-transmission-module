# 檔案傳輸模擬環境與視覺化管理介面 (Docker Compose Guide)

本目錄提供用於本機開發、功能驗證與 Swagger 介面測試的完整 Docker 模擬環境。一鍵即可同時啟動 **SFTP**、**FTP**、**FTPS (TLS 加密)** 三大傳輸伺服器，並整合 **FileBrowser** 作為即時視覺化檔案管理 Web UI。

---

## 1. 系統拓撲與架構總覽 (Architecture)

```
                            +-------------------------------------------------+
                            |   FileBrowser Web UI (http://localhost:8088)     |
                            |   [免密碼直接檢視, 支援預覽/拖拉上傳/下載/刪除]  |
                            +-------------------------------------------------+
                                                     |
                                   共用本機磁碟掛載 (./docker-data:/srv)
                                 /                   |                  \
                                v                    v                   v
                    ./docker-data/sftp/      ./docker-data/ftp/   ./docker-data/ftps/
                             |                       |                   |
                             v                       v                   v
                  +--------------------+   +-------------------+  +-------------------+
                  | mock-sftp-server   |   | mock-ftp-server   |  | mock-ftps-server  |
                  | Port: 2222 (SSH)   |   | Port: 2121 (FTP)  |  | Port: 2122 (FTPS) |
                  +--------------------+   +-------------------+  +-------------------+
```

---

## 2. 服務與通訊埠配置清單 (Services & Ports)

| 服務項目 | 容器名稱 (Container) | 協定 (Protocol) | 對外通訊埠 (Host Port) | 被動模式埠 (PASV Ports) | 預設帳號 / 密碼 | 遠端根目錄路徑 | 本機持久化掛載路徑 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **SFTP** | `mock-sftp-server` | SFTP (SSH) | `2222` | - | `tester` / `password` | `/upload` | `./docker-data/sftp` |
| **FTP** | `mock-ftp-server` | FTP (明文) | `2121` | `21100-21110` | `tester` / `password` | `/` | `./docker-data/ftp` |
| **FTPS** | `mock-ftps-server` | FTPS (TLS Explicit) | `2122` | `21120-21130` | `tester` / `password` | `/` | `./docker-data/ftps` |
| **Web UI** | `mock-file-manager-ui` | HTTP (FileBrowser) | `8088` | - | 免登入 (`--noauth`) | - | `./docker-data` |
| **MSSQL** | `mock-mssql-server` | TDS (SQL Server 2022) | `1433` | - | `sa` / `Root@123456` | - | `mssql-data` (Docker 具名卷軸) |

---

## 3. 快速操作指令 (Quick Start)

請於終端機進入 `docker-compose` 目錄執行以下指令：

### 啟動所有模擬伺服器
```powershell
docker compose up -d
```

### 檢視所有容器狀態
```powershell
docker compose ps
```

### 檢視即時日誌 (除錯用)
```powershell
# 檢視 FTPS TLS 交握日誌
docker compose logs -f ftps-server

# 檢視 SFTP 登入日誌
docker compose logs -f sftp-server

# 檢視 FTP 控制通道日誌
docker compose logs -f ftp-server

# 檢視 MSSQL 資料庫啟動日誌
docker compose logs -f mssql-server
```

### 停止並移除所有容器
```powershell
docker compose down
```

> [!TIP]
> 若欲連同測試過程上傳的所有檔案一同清空重置，可手動刪除 `docker-data/` 目錄：
> ```powershell
> Remove-Item -Recurse -Force ./docker-data
> ```

---

## 4. Swagger UI 測試連線參數 (Swagger Test Guide)

啟動 Spring Boot 應用程式後，瀏覽 [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)，依據測試的傳輸協定於 Request Body 填入相應連線資訊：

### (1) SFTP 上傳測試範例 (`/api/v1/transmission/upload`)
```json
{
  "host": "127.0.0.1",
  "port": 2222,
  "username": "tester",
  "password": "password",
  "protocol": "SFTP",
  "timeoutMs": 10000,
  "passiveMode": true,
  "localFilePath": "D:/test-sample.txt",
  "remotePath": "/upload",
  "filename": "sftp-uploaded.txt"
}
```

### (2) FTP 上傳測試範例 (`/api/v1/transmission/upload`)
```json
{
  "host": "127.0.0.1",
  "port": 2121,
  "username": "tester",
  "password": "password",
  "protocol": "FTP",
  "timeoutMs": 10000,
  "passiveMode": true,
  "localFilePath": "D:/test-sample.txt",
  "remotePath": "/",
  "filename": "ftp-uploaded.txt"
}
```

### (3) FTPS (Explicit TLS) 上傳測試範例 (`/api/v1/transmission/upload`)
```json
{
  "host": "127.0.0.1",
  "port": 2122,
  "username": "tester",
  "password": "password",
  "protocol": "FTPS",
  "timeoutMs": 10000,
  "passiveMode": true,
  "localFilePath": "D:/test-sample.txt",
  "remotePath": "/",
  "filename": "ftps-uploaded.txt"
}
```

---

## 5. MSSQL 資料庫連線配置 (Microsoft SQL Server Guide)

容器中運行的是 **Microsoft SQL Server 2022 Developer 版**，可作為本專案持久化資料庫或整合測試之用。

### (1) 連線參數
* **主機位址 (Host)**：`127.0.0.1` (或 `localhost`)
* **通訊埠 (Port)**：`1433`
* **管理者帳號 (Username)**：`sa`
* **管理者密碼 (Password)**：`Root@123456`
* **預設資料庫 (Database)**：`master` (或自行建立專屬資料庫如 `transmission_db`)

### (2) JDBC 連線字串 (JDBC URL)
```properties
jdbc:sqlserver://localhost:1433;databaseName=master;trustServerCertificate=true;encrypt=false
```

### (3) Spring Boot `application.properties` 設定範例
若未來欲將本專案由 H2 切換為 MSSQL，可加入以下連線配置：
```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=master;trustServerCertificate=true;encrypt=false
spring.datasource.username=sa
spring.datasource.password=Root@123456
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.SQLServerDialect
```

### (4) GUI 管理工具連線 (DBeaver / DataGrip / SSMS)
* **Authentication**：SQL Server Authentication
* **Username**：`sa`
* **Password**：`Root@123456`
* **Trust Server Certificate**：勾選 True (因 Docker 為自簽憑證)

---

## 6. Web UI 視覺化檔案管理 (FileBrowser)

* **訪問網址**：[http://localhost:8088](http://localhost:8088)
* **主要功能**：
  1. **實時對帳與驗證**：Spring Boot 只要透過 API 完成上傳或目錄同步，開啟網頁便能即時於 `/sftp`、`/ftp`、`/ftps` 資料夾下看見剛剛傳輸成功的檔案。
  2. **快速準備測試資料**：可直接在 UI 介面上拖拉檔案上傳至 `/sftp` 或 `/ftp`，即可快速作為「下載檔案」或「目錄同步」的測試來源資料。
  3. **線上內容檢視**：支援文字檔案、JSON、圖片等直接在瀏覽器線上預覽，不需下載至本機確認。

---

## 7. 技術設計與常見問題說明 (Technical Notes)

1. **FTP / FTPS 被動模式 (PASV Mode) 與 Docker 網路穿透**：
   * FTP 在二進位資料傳輸時會開闢獨立的 Data Channel。容器環境下若無指定 `ADDRESS` / `PUBLICHOST`，伺服器會將容器內部虛擬 IP（如 `172.x.x.x`）回傳給本機客戶端，導致資料連線逾時。
   * 本設定已將被動通道位址強制指向 `127.0.0.1`，並將對應的埠範圍（`21100-21110` 與 `21120-21130`）完整映射至本機宿主機。
2. **FTPS 自簽憑證信任 (Trust All Certificates)**：
   * `mock-ftps-server` 啟動時會自動產生一組自簽 TLS 憑證。
   * 本專案的 `FtpsTransmissionClient` 已在內部配置了 `TrustManagerUtils.getAcceptAllTrustManager()`，可在開發與測試環境中順暢連線，不會觸發 Java `PKIX path building failed` 異常。
3. **MSSQL 資料庫持久化 (Named Volume)**：
   * MSSQL 使用 Docker 具名卷軸 `mssql-data` 掛載 `/var/opt/mssql/data`，避免 Windows 主機目錄權限衝突，並維持容器重啟時資料不遺失。

