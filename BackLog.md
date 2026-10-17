# 檔案傳輸模組需求清單與架構優化 (BackLog.md)

本文件用於記錄專案中暫時擱置、待評估或規劃於未來迭代中實現的技術債、架構優化與功能擴展項目。

---

## 項目清單 (Backlog Items)

### [BL-001] EDA (Event-Driven Architecture) 事件驅動架構設計與優化

* **項目類別**：架構優化 / 功能擴展 (Architectural Optimization / Future Feature)
* **當前狀態**：規劃中 / 暫時擱置 (Deferred / Shelved)
* **優先級別**：Medium (中等)
* **紀錄日期**：2026-10-17

#### 1. 背景與決策緣由 (Context & Decision)
* **初步設計**：
  模組初期採用領域驅動設計 (DDD) 的事件驅動架構 (EDA)，由傳輸工作階段實體 (`TransmissionSession`) 累積領域事件，並在應用層使用案例 (`UploadFileUseCase`, `DownloadFileUseCase`, `SyncDirectoryUseCase`) 執行完畢後，透過輸出埠 (`TransmissionEventPublisherPort`) 發布領域事件至 Spring `ApplicationEventPublisher` 內部匯流排。
* **擱置決策**：
  考量本專案定位為獨立檔案傳輸功能模組，核心聚焦於 FTP、FTPS、SFTP 協定傳輸與目錄同步之穩定性與型別安全性。現階段若維持完整的 EDA，將帶來額外的事件契約版本控管、非同步追蹤成本與測試 mock 負擔。因此依據架構精簡原則，**暫時捨棄 EDA**，將傳輸階段精簡為純粹的狀態流轉實體 (`TransmissionSession` Entity)，解除對事件發布的依賴。

---

#### 2. 原設計資產與事件契約存檔 (Design Assets & Specifications)
未來重啟 EDA 時，可直接沿用以下已定義完整的領域事件與六角形架構合約：

##### (1) 領域事件契約 (Domain Events)
| 事件名稱 | 觸發時機 | 攜帶重要資料 |
| :--- | :--- | :--- |
| `FileUploadedEvent` | 單一檔案上傳成功至遠端主機後觸發 | `sessionId`, `protocol`, `sourcePath`, `targetPath`, `fileName`, `fileSizeBytes` |
| `FileDownloadedEvent` | 單一檔案自遠端主機下載至本機後觸發 | `sessionId`, `protocol`, `sourcePath`, `targetPath`, `fileName`, `fileSizeBytes` |
| `DirectorySynchronizedEvent` | 本機與遠端目錄同步完成後觸發 | `sessionId`, `protocol`, `direction`, `localDir`, `remoteDir`, `transferredCount`, `deletedCount` |

##### (2) 六角形架構埠與適配器 (Ports & Adapters)
* **Outbound Port (應用層合約)**：
  - `com.example.demo.application.port.out.TransmissionEventPublisherPort`
  - 核心方法：`void publish(Object event);`
* **Outbound Adapter (基礎設施適配器)**：
  - `com.example.demo.infrastructure.adapter.TransmissionEventPublisherAdapter`
  - 實作技術：`org.springframework.context.ApplicationEventPublisher`

---

#### 3. 未來重啟 EDA 的觸發條件 (Trigger Conditions & Roadmap)
當專案演進滿足下列任一業務或非功能性需求時，應評估重新引入 EDA：

1. **稽核與軌跡日誌 (Audit Logging)**：
   - 需要將所有傳輸成功的檔案名、大小、起訖時間非同步寫入 Elasticsearch、關聯式資料庫或獨立稽核系統。
2. **非同步通知與告警 (Notifications & Webhooks)**：
   - 傳輸完成後需非同步發送 Webhook 回調、電子郵件、Slack 或 Teams 告警，且通知行為不可阻塞傳輸主執行緒。
3. **分散式訊息匯流排與微服務解耦 (MQ Integration)**：
   - 當本模組需支援高併發或作為微服務獨立運作時，將事件轉發至 Apache Kafka 或 RabbitMQ 供下游消費者（如 ETL 管線、檔案病毒掃描服務）訂閱消費。
4. **即時監控指標 (Prometheus / Grafana Metrics)**：
   - 需透過事件監聽器即時計算檔案傳輸流量、頻寬消耗與傳輸成功率等 APM 監控指標。

---

### [BL-002] 檔案傳輸與資料庫狀態最終一致性與補償機制 (Eventual Consistency & Compensation)

* **項目類別**：可靠性與資料一致性 (Data Consistency / Reliability / Architecture Optimization)
* **當前狀態**：規劃中 / 待排程 (Planned / Backlog)
* **優先級別**：High (高)
* **紀錄日期**：2026-10-17

#### 1. 背景與問題分析 (Background & Problem Analysis)
本模組在執行檔案傳輸時，涉及兩大異質系統的協同運作：
1. **本機關聯式資料庫 (RDBMS)**：記錄傳輸工作階段 (`TransmissionSessionEntity`) 的生命週期狀態 (`IN_PROGRESS`, `COMPLETED`, `FAILED`)。
2. **遠端通訊協定伺服器 (FTP / FTPS / SFTP)**：執行實體檔案位元組的網路 I/O 寫入。

##### 核心挑戰：
* **無分散式交易支援**：外部 FTP/SFTP 伺服器不支援 2PC (兩階段提交) 或 XA 分散式事務，本質上屬於雙寫問題 (Dual-Write Problem)，在理論上無法單靠本機資料庫交易達到「強一致性 (Strict ACID Consistency)」。
* **嚴禁長事務 (@Transactional Anti-Pattern)**：若直接在 `uploadFile` 上標註 `@Transactional`，會導致資料庫連線在檔案長傳輸期間被鎖死占用（導致連線池枯竭）；且一旦傳輸失敗拋出例外造成 Transaction Rollback，會將原先寫入的 `IN_PROGRESS` 稽核軌跡抹除，造成嚴重的紀錄丟失。

---

#### 2. 潛在不一致情境 (Failure Scenarios & Risks)
1. **上傳成功但 DB 更新失敗（孤兒檔案 Orphan Files）**：
   - 遠端伺服器已完整接收檔案，但在發動 `recordUploadSuccess()` 時遭遇 DB 暫時性斷線、鎖逾時或應用程式重啟，導致 DB 狀態停滯在 `IN_PROGRESS`，客戶端誤判傳輸失敗。
2. **傳輸中途中斷（半截/損毀檔案 Corrupt Files）**：
   - 檔案傳輸到一半（如傳送 60%）發生網路中斷，Catch 區塊將 DB 標記為 `FAILED`，但遠端已留下未完成的殘缺檔案，可能被下游系統錯誤讀取。
3. **伺服器非預期崩潰（狀態永遠滯留）**：
   - 應用服務在傳輸進行中因斷電或 OOM 終止，未進入 Catch 區塊，該筆紀錄將永遠留在 `IN_PROGRESS`。

---

#### 3. 規劃之技術解決方案與實作藍圖 (Technical Solutions & Roadmap)

針對上述問題，將透過**最終一致性 (Eventual Consistency)**、**暫存與原子重命名**與**補償交易 (Saga Pattern)** 達成高可靠保證：

##### (1) 遠端暫存檔與原子重命名 (Staging & Atomic Rename)
* **作法**：檔案上傳時不直接寫入正式檔名（如 `data.txt`），而是寫入暫存檔名（如 `data.txt.tmp.<trackingId>`）。
* **效益**：
  * 全部資料傳輸且校驗完畢後，透過 SFTP/FTP 協定的 `RENAME` 指令進行原子更名。
  * 傳輸中途中斷時，遠端僅殘留 `.tmp` 暫存檔，不會污染正式業務檔案。

##### (2) 異常捕獲與清理補償機制 (Compensating Transaction)
* **作法**：在 `FileTransmissionCommandService` 的 `catch (Exception ex)` 區塊中，發動業務補償呼叫：
  ```java
  try {
      fileTransmissionGatewayPort.deleteRemoteFileIfExists(command.serverConfig(), tempFilePath);
  } catch (Exception cleanupEx) {
      log.warn("補償清理遠端暫存檔失敗 [trackingId: {}]: {}", trackingId, cleanupEx.getMessage());
  }
  ```
* **效益**：發生網路逾時或異常時，主動清除遠端可能已建立的不完整檔案。

##### (3) 檔案大小與雜湊校驗 (Integrity Verification)
* **作法**：檔案上傳完成後，調用協定端點取得遠端檔案大小（Byte Length）或校驗碼（Checksum / Hash），確認與本機來源檔案完全一致後，才觸發 `recordUploadSuccess()`。

##### (4) 背景定時對帳與逾時自癒排程 (Reconciliation & Self-Healing Job)
* **作法**：建立背景排程 Job（例如每 10 分鐘執行一次），掃描逾期（例如建立超過 30 分鐘）仍處於 `IN_PROGRESS` 的任務：
  1. 連線至遠端主機探測：
     - 若正式檔案完整存在且大小相符 &rarr; 自動補齊狀態為 `COMPLETED`。
     - 若僅有殘留暫存檔或檔案不存在 &rarr; 清除暫存檔並標記為 `FAILED (TIMEOUT_RECONCILED)`。
* **效益**：即使應用程式發生斷電或宕機，亦能具備自動對帳與自癒修復能力。

---

#### 4. 影響範圍與元件變更評估 (Affected Components)
* **應用層輸出埠 (Outbound Port)**：
  - `FileTransmissionGatewayPort`：擴充 `deleteRemoteFileIfExists`、`renameRemoteFile`、`getRemoteFileSize`。
* **基礎設施層協定適配器 (Adapters & Clients)**：
  - `FileTransmissionGatewayAdapter`、`SftpTransmissionClient`、`FtpTransmissionClient`、`FtpsTransmissionClient` 實作對應原子重命名與刪除指令。
* **應用層服務 (Application Service)**：
  - `FileTransmissionCommandService`：整合暫存上傳、更名與例外清理補償。

---

### [BL-003] 分散式傳輸冪等性與防重複上傳機制 (Distributed Idempotency & Deduplication)

* **項目類別**：高可用性與分散式並發控制 (Distributed Concurrency Control / Idempotency / High Availability)
* **當前狀態**：規劃中 / 待排程 (Planned / Backlog)
* **優先級別**：High (高)
* **紀錄日期**：2026-10-17

#### 1. 背景與核心問題分析 (Background & Problem Analysis)
在微服務與叢集多節點 (Multiple Pods / Instances) 部署架構下，檔案傳輸常面臨重複請求與競爭並發風險：
1. **上游呼叫重試 (Client / Gateway Retry)**：當網路暫時性抖動或傳輸耗時較長時，API Gateway 或調用方客戶端可能自動重試，導致多個節點同時發起相同檔案之上傳。
2. **多節點同時寫入衝突 (Concurrent Write Corruption)**：
   - 目前實作（如 `SftpTransmissionClient`）直接以 `sftp.put(inputStream, filename)` 寫入目標路徑。
   - 若節點 A 與節點 B 同時向遠端伺服器上傳同名檔案，將發生 I/O 寫入衝突，造成遠端檔案內容交錯損毀或資料不完整。
3. **無預警靜默覆蓋 (Silent Overwrite)**：後完成的請求會直接覆蓋先完成的檔案，造成歷史版本丟失且缺乏審計軌跡。
4. **缺乏業務冪等性 (Lack of Idempotency)**：API 請求未攜帶業務唯一標識，系統重複耗費網路頻寬與 I/O 資源重複傳輸相同內容。

---

#### 2. 規劃之改造方案矩陣 (Transformation Solutions Matrix)

為解決分散式重複上傳問題，規劃以下四種不同深度與依賴程度之技術改造方案：

| 方案名稱 | 適用場景 | 外部相依性 | 核心實作技術 | 優點 | 限制與注意事項 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **方案 1：遠端檔案衝突檢查 + 臨時檔原子重命名** | 輕量化獨立模組部署、無 Redis/DB 中介 | **零外部相依** (僅依賴 SFTP/FTP 本身協定) | `exists()` 預檢 + `.tmp.<id>` 上傳 + `rename()` | 架構最簡潔，開箱即用，能避免傳輸中檔案損壞與覆蓋 | 在毫秒級高並發下，兩節點同時執行 `exists()` 仍可能存在極小時間差 (Race Condition) |
| **方案 2：全域業務冪等鍵 + 資料庫唯一約束** | 專案已具備關聯式資料庫 (RDBMS) | **關聯式資料庫** (PostgreSQL / MySQL) | `idempotencyKey` + `UNIQUE INDEX` | 資料庫級強一致性保證，自然支援狀態快取與查詢回傳 | 依賴資料庫交易，大檔案高並發請求仍會觸發短暫連線開銷 |
| **方案 3：分散式鎖機制 (Distributed Lock)** | 微服務高並發叢集、分散式排程環境 | **Redis / Redisson** | `RLock` / Lua Script + Watchdog 續租 | 即時阻斷跨節點並發重複請求，支援鎖等待與重試 | 需引入並維運 Redis 基礎設施叢集 |
| **方案 4：防護縱深綜合防禦架構 (推薦最終形態)** | 金融級、企業級關鍵檔案傳輸場景 | **Redis + DB + 協定端點** | 分層過濾：API 冪等鍵 &rarr; Redis 分散式鎖 &rarr; DB 唯一索引 &rarr; 遠端暫存原子更名 | 防護最周密，兼顧防重、防損、可追蹤與故障自癒 | 開發與維運成本相對較高 |

---

#### 3. 各方案詳細技術實作規範 (Detailed Technical Specifications)

##### (1) 方案 1：遠端檔案衝突檢查與原子重命名 (Remote Existence & Atomic Rename)
* **實作流程**：
  1. **存在預檢 (Pre-check)**：
     - `FileTransmissionGatewayPort` 擴充 `boolean exists(TransmissionServerConfig config, RemoteFilePath remotePath, String filename)`。
     - 若檔案已存在且請求參數 `overwrite` 為 `false`，直接拋出 `FileAlreadyExistsException`（表現層對應回傳 `HTTP 409 Conflict`）。
  2. **隔離寫入 (Isolated Upload)**：
     - 上傳目標暫定為遠端臨時檔名：`${filename}.uploading.${trackingId}`。
  3. **原子提交 (Atomic Commit)**：
     - 傳輸完成且大小驗證無誤後，透過協定層執行原子更名：`RENAME ${filename}.uploading.${trackingId} -> ${filename}`。
     - 若遠端已存在同名檔案（且允許覆蓋），依協定支援度先刪除舊檔或原子置換。

##### (2) 方案 2：業務冪等鍵與資料庫防重 (Idempotency Key & DB Unique Constraint)
* **實作流程**：
  1. **請求層增加冪等標識**：
     - 在 `UploadFileResource` 與 `UploadFileCommand` 中新增 `idempotencyKey` 欄位（可由客戶端 Header `X-Idempotency-Key` 提供，或依 `SHA-256(localFileContent) + targetPath` 計算產生）。
  2. **資料庫唯一約束**：
     - 在傳輸工作階段表建立唯一索引：
       ```sql
       ALTER TABLE transmission_session ADD CONSTRAINT uk_transmission_idempotency_key UNIQUE (idempotency_key);
       ```
  3. **應用層狀態流轉處理**：
     - 寫入時若捕獲 `DataIntegrityViolationException`，反查該 `idempotencyKey` 紀錄：
       - 若狀態為 `COMPLETED` &rarr; 直接返回先前傳輸成功的快取結果（保證冪等性）。
       - 若狀態為 `IN_PROGRESS` &rarr; 拋出 `TransmissionInProgressException`（回傳 `HTTP 409 Conflict` 或 `HTTP 202 Accepted`，告知正在處理中）。

##### (3) 方案 3：分散式鎖機制 (Distributed Lock via Redisson)
* **實作流程**：
  1. **鎖鍵規則 (Lock Key Pattern)**：
     - 鍵名採用目標端點資源唯一標識：`lock:transmission:upload:{host}:{remotePath}:{filename}`。
  2. **鎖定生命週期 (Lock Lifecycle)**：
     ```java
     RLock lock = redissonClient.getLock(lockKey);
     // 嘗試獲取鎖：最多等待 3 秒，上鎖成功後由 Watchdog 自動續租（防止長傳輸時鎖逾時釋放）
     if (lock.tryLock(3, TimeUnit.SECONDS)) {
         try {
             // 執行預檢、傳輸與狀態記錄
         } finally {
             lock.unlock();
         }
     } else {
         throw new ConcurrentTransmissionException("已有相同的檔案傳輸正在進行中，請勿重複提交: " + filename);
     }
     ```
  3. **降級與重試 (Fallback & Retry)**：
     - 未取得鎖之節點可直接快速失敗（Fast-Fail），或註冊監聽等待鎖釋放後直接獲取傳輸成果。

---

#### 4. 預期涉及之元件與套件異動 (Expected Affected Components)

* **表現層 (Presentation Layer)**：
  - `UploadFileResource`：新增可選之 `idempotencyKey` 屬性。
  - `GlobalExceptionHandler`：新增 `FileAlreadyExistsException` (409) 與 `ConcurrentTransmissionException` (409/429) 之異常處理分支。
* **應用層 (Application Layer)**：
  - `UploadFileCommand`：新增 `idempotencyKey` 欄位。
  - `FileTransmissionGatewayPort`：新增 `exists()`、`renameFile()` 抽象方法。
  - `FileTransmissionCommandService`：整合冪等鍵檢核、遠端衝突確認與原子重命名流程。
* **基礎設施層 (Infrastructure Layer)**：
  - `SftpTransmissionClient`, `FtpTransmissionClient`, `FtpsTransmissionClient`：實作 `exists` (利用 `sftp.stat()` / `ftp.mlistFile()`) 與更名邏輯。
  - (若選用方案 3) 新增 `DistributedLockPort` (Outbound Port) 與 `RedissonDistributedLockAdapter` (Outbound Adapter)。

