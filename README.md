# File Transmission Module (檔案傳輸模組)

企業級檔案傳輸微服務模組，支援 **FTP**、**FTPS (Explicit TLS)** 與 **SFTP** 協定，提供檔案上傳、下載、目錄同步與傳輸歷史稽核紀錄功能。

本專案遵循 **Domain-Driven Design (DDD)** 與 **六角形架構 (Clean Architecture / Hexagonal Architecture)**，並具備高規格的密碼機密防護機制。

---

## 核心功能特色

1. **多傳輸協定支援**
   - **FTP (File Transfer Protocol)**：標準明文傳輸（支援 Passive 模式）。
   - **FTPS (Explicit TLS FTP)**：使用 `AUTH TLS`，自動執行 `PROT P` 資料通道加密，並支援信任自簽憑證 (`TrustAllSSLSocketFactory`)。
   - **SFTP (SSH File Transfer Protocol)**：基於 JSch 的 SSH2 加密安全通道，支援自訂 Port、超時與 StrictHostKeyChecking 彈性配置。
2. **傳輸業務場景**
   - **檔案上傳 (Upload)**：將本機檔案推送至遠端主機，支援自動建立遠端階層目錄與傳輸進度監控。
   - **檔案下載 (Download)**：自遠端下載指定路徑檔案至本地，支援自動遞迴建立目錄與覆蓋機制。
   - **目錄雙向同步 (Sync Directory)**：支援比對遠端與本地目錄內容，進行增量或全量檔案同步。
3. **安全防護與機密隔離 (Security Hardening)**
   - **AES-256-GCM 密碼加密防護**：傳輸主機連線密碼一律經由 `TransmissionCipher` 加密傳輸與存放。
   - **外部化金鑰配置**：支援透過 `application.properties`、作業系統環境變數或 Key Vault 載入主金鑰，避免金鑰硬編碼。
   - **防止記憶體與日誌洩漏**：實體物件 (`TransmissionServerConfig`) 具備不可變設計，其 `toString()` 方法預設自動遮蔽密碼 (`******`)，確保日誌永不出現敏感機密。
4. **企業級儲存與稽核 (Audit Log & MSSQL)**
   - 整合 Spring Data JPA 與 Microsoft SQL Server (MSSQL)。
   - 傳輸任務狀態紀錄 (`transmission_task`)，支援斷點追蹤與傳輸耗時統計。

---

## 架構分層設計 (Clean Architecture & DDD)

本專案遵循嚴格的分層邊界與防腐機制：

```
src/main/java/com/example/demo/
├── domain/                               # 【領域核心層】純 Java，零技術框架侵入
│   └── transmission/
│       └── aggregate/
│           ├── root/Task.java           # 傳輸任務聚合根
│           ├── entity/TaskStep.java     # 任務步驟實體
│           └── vo/                      # 實質物件 (TransmissionServerConfig, TaskId, TaskStatus 等)
├── application/                          # 【應用服務層】UseCase 流程編排與 CQRS
│   ├── port/in/                         # Inbound Ports (UploadFileUseCase, DownloadFileUseCase...)
│   ├── port/out/                        # Outbound Ports (TaskRepositoryPort, CredentialCipherPort...)
│   ├── service/                         # UseCase 實作 (FileTransmissionCommandService, QueryService)
│   ├── command/                         # Command 命令物件
│   ├── query/                           # Query 查詢物件
│   ├── dto/                             # 資料傳輸載體 (純資料，無轉換邏輯)
│   └── assembler/                       # DTO Assembler 轉換器
├── infrastructure/                       # 【基礎設施層】技術實作與適配器
│   ├── adapter/                         # Outbound Adapters (TaskRepositoryAdapter, CredentialCipherAdapter)
│   ├── transmission/                    # FTP / FTPS / SFTP Client 實作
│   └── persistence/                     # Spring Data JPA 實體與 Repository
├── presentation/                         # 【表現層】RESTful Web API
│   ├── controller/                      # FileTransmissionCommandController, QueryController
│   ├── resource/                        # Inbound (Request) 與 Outbound (Response) 物件
│   └── assembler/                       # Presentation 資源轉換器
└── config/                              # 【全域配置層】Bean 宣告、Properties 與 Spring 裝配
```

---

## 安全配置說明

主金鑰預設由 `application.properties` 或環境變數注入：

```properties
# application.properties
transmission.security.cipher.secret-key=YourSecure32ByteBase64SecretKeyHere
transmission.security.cipher.algorithm=AES/GCM/NoPadding
transmission.security.cipher.tag-length=128
```

或使用環境變數（生產環境推薦）：
```bash
export TRANSMISSION_SECURITY_CIPHER_SECRET_KEY="<Base64-Encoded-AES-Key>"
```

更多金鑰輪替、密碼產生與維護指引，請參閱 [SECURITY_MAINTENANCE_GUIDE.md](doc/maintainance/SECURITY_MAINTENANCE_GUIDE.md)。

---

## 本地測試與驗證

1. **單元測試 (Unit Tests)**：
   ```bash
   ./mvnw test
   ```
   所有 UseCase、領域模型與加解密機制均具備 100% 通過的 BDD (Gherkin 語法) 單元測試。

2. **Docker Compose 整合測試環境**：
   專案附帶測試用 SFTP / FTPS Docker 容器配置：
   ```bash
   cd docker-compose
   docker-compose up -d
   ```
