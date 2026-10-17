Created At: 2026-10-16T19:08:58+08:00
Completed At: 2026-10-16T19:08:58+08:00
File Path: `file:///d:/%E6%A1%8C%E9%9D%A2/ModuleWorkSpace/file-transmission-module/.agents/AGENTS.md`
Total Lines: 67
Total Bytes: 9697
Showing lines 1 to 67
The following code has been modified to include a line number before every line, in the format: <line_number>: <original_line>. Please note that any changes targeting the original code should remove the line number, colon, and leading space.
1: # WorkSpace 開發規範與架構準則 (AGENTS.md)
2: 
3: 本工作區（WorkSpace）內的所有後端專案與開發任務，必須遵循以下三大架構設計與命名規範：
4: 
5: ## 1. Domain Driven Design (DDD, 領域驅動設計)
6: - **核心領域至上**：領域模型（Domain Model）須封裝完整的商業規則與領域邏輯（例如 Aggregate Root 聚合根、Value Object 實質物件與 Domain Event 領域事件）。
7: - **零框架與零外層依賴**：`domain/` 層內的程式碼為純 Java（Pure Java），**絕對不可**依賴 Spring 框架、JPA 註解、資料庫相關技術，亦**嚴禁**依賴外層的 `application/` 或 `infrastructure/` 套件。
8: - **嚴格禁止 Lombok 等技術類侵入 Domain Layer**：`domain/` 層內部嚴禁引入或依賴任何框架、資料庫 ORM（如 JPA `Entity`、`Table` 註解、Hibernate）、序列化套件（如 Jackson `@JsonProperty`）、HTTP 協定物件或任何外部基礎設施相關套件，保證領域邏輯的純粹與技術無關性。此外，**Aggregate 內部嚴禁使用 Lombok (如 `@Data`, `@Getter`, `@Setter` 等)**，以確保封裝性不被破壞，屬性必須透過具備業務語意的行為方法進行異動。
9: - **領域事件驅動**：當領域狀態變更時，經由 Aggregate 觸發並對外發布對應的 Domain Event。
10: - **垂直切片與 Aggregate 聚合 (Vertical Slice Architecture)**：
11:   - Domain Layer 內部**必須依據 Aggregate (聚合根)** 進行切片與封裝（例如 `domain/task/` 作為 Aggregate 邊界），將該 Aggregate 專屬的 `aggregate/`（Aggregate Root 與 Value Objects）、`event/`（Domain Events）與 `exception/`（領域例外）內聚於此，嚴禁散落於全域共用目錄。
12:     - `aggregate/`：該 Aggregate 的領域模型內部可進一步細分為 `root/` (Aggregate Root 聚合根，如 `Task`)、`entity/` (內部實體，如 `TaskStep`) 與 `vo/` (Value Objects 實質物件，如 `TaskId`, `TaskStatus`)。
13:     - `event/`：該 Aggregate 觸發的領域事件 (例如 `TaskCreatedEvent`, `TaskUpdatedEvent`)。
14:     - `exception/`：該 Aggregate 專屬的領域例外 (例如 `TaskNotFoundException`)。
15: 
16: ## 2. Clean Architecture & 六角形架構 (Hexagonal Architecture / Ports & Adapters)
17: 後端程式碼須依據分層結構清楚隔離，並明確規範 **Port 與 Adapter 的放置規則**：
18: - **Port 方法嚴禁接發技術類物件**：所有 Input Port (`port/in/`) 與 Output Port (`port/out/`) 介面的方法，**傳參及回傳型別嚴禁使用任何技術類或環境相依的物件**（如 ORM 實體 `TaskJpaEntity`、資料庫連線 `Connection`、HTTP 請求/回應物件 `HttpServletRequest` / `ResponseEntity` 等）；Port 僅可接收並回傳領域物件（Aggregate Root, Value Object）、Command、Query、DTO 與 Projection。
19: - **Application Layer (應用層)**
20:   - 負責 CQRS 流程調度、Command/Query 物件、Dto 與 View Projection。
21:   - **Dto 與 View 嚴格作為純資料載體**：Dto (`TaskGottenResult`) 與 View (`TaskGottenView`) 不應負責轉換工作（嚴守單一職責，禁止在 Dto 內部寫入 `fromDomain` 等轉換邏輯）；領域模型與 Dto/View 的轉換必須獨立於 `dto/` 之外的 `assembler/` 套件中的 Assembler / Mapper 物件（例如 `TaskDtoAssembler`）負責執行。
22:   - **Input Port 與 Output Port 統一放置於 Application 層 (`port/`)**：
23:     - `port/in/` (Inbound Ports / Use Cases)：代表應用層對外開放的業務合約（例如 `CreateTaskUseCase`、`UpdateTaskUseCase`、`GetTaskUseCase`），接收 Command/Query 並回傳 DTO/View。
24:     - `port/out/` (Outbound Ports)：代表應用層對外要求基礎設施提供的合約（包含 JPA 資料庫、MQ 訊息佇列、快取、外部服務介面，例如 `TaskRepositoryPort`、`TaskMessagePublisherPort`）。
25:   - `service/`：實作 `port/in/` 中的 UseCase 介面，協調 Domain Aggregate 與 Application Outbound Port (`port/out/`)。
26: - **Infrastructure Layer (基礎設施層 - Outbound Adapters)**
27:   - **模組隔離與介面設計**：
28:     - `persistence/`：直接位於 `infrastructure/` 下，內部**依據 Aggregate 名稱直接分類**（如 `persistence/task/`），並進一步拆分為 `entity/`（如 ORM 實體 `TaskJpaEntity`）與 `repository/`（如 Spring Data Repository `TaskJpaRepository`）。
29:     - `messaging/`：直接位於 `infrastructure/` 下，放置訊息佇列的 Broker 介接模組（如 `TaskMqProducer`）。
30:     - `adapter/`：作為**所有基礎設施模組的匯聚點與對外出口**，放置實作 Application 層 Outbound Port 的適配器（如 `TaskRepositoryAdapter`、`TaskMessagePublisherAdapter`），由其協調並呼叫 `persistence/`、`messaging/` 等其他模組。
31: - **Presentation Layer (表現層 - Inbound Adapters)**
32:   - REST Controller 或其他對外 API 介面，接收 Request 轉換為 Command/Query，呼叫應用層的 UseCase 並將結果封裝為 Response。
33:   - **Resource 嚴格作為純資料載體與 Assembler 負責轉換 (`assembler/`)**：
34:     - Request/Response Resource 物件必須維持嚴格的純資料載體（Pure Data Carrier），**不得於 Resource 內部實作 `toCommand` 或 `fromResult` 等任何轉換邏輯**。
35:     - 表現層與應用層的參數資料轉換（防腐層機制 Anti-Corruption Layer），**一律交由獨立於 `resource/` 之外的 `assembler/` 套件的 Assembler / Mapper 物件（例如 `TaskResourceAssembler`）負責執行**。
36:   - **Resource 命名與分層規範 (`resource/`)**：
37:     - `resource/in/` (Request Data)：對外接收的請求載體 (`V + N + Resource`，如 `CreateTaskResource`、`UpdateTaskResource`)。
38:     - `resource/out/` (Response Data)：對外回傳的結果載體 (`N + Ved + Resource`，如 `TaskCreatedResource`、`TaskRetrievedResource`)。
39: - **Configuration Layer (配置層 - The Dirtiest Layer)**
40:   - **全域配置集中存放**：包含 Spring Configuration (`@Configuration`)、Bean 註冊、Web/WebSocket 設定、Security 等技術耦合度極高的配置檔，**一律統一放置於專案根目錄下的 `config/` 套件中**。
41:   - 因為它是系統中最依賴技術細節的 (Dirtiest) 的一層，專門負責將所有層與基礎設施組裝起來，**嚴禁**將配置類散落於 Domain、Application 或其他分層模組內。
42: 
43: ## 3. 嚴格的物件命名規範 (9 大類別)
44: 
45: 定義任何 DTO、請求、回應、事件、命令、查詢、領域例外以及 Port 與 Adapter 時，必須嚴格依照以下命名規範：
46: | 類別 | 命名規範 | 說明與範例 |
47: | --- | --- | --- |
48: | **3-1. Request** | `V + N + Resource` | 動詞 + 名詞 + Resource (例如：`CreateTaskResource`, `UpdateTaskResource`) |
49: | **3-2. Response** | `N + Ved + Resource` | 名詞 + 過去分詞 + Resource (例如：`TaskCreatedResource`, `TaskUpdatedResource`, `TaskRetrievedResource`) |
50: | **3-3. Event** | `N + Ved + Event` | 名詞 + 過去分詞 + Event (例如：`TaskCreatedEvent`, `TaskUpdatedEvent`) |
51: | **3-4. Command** | `V + N + Command` | 動詞 + 名詞 + Command (例如：`CreateTaskCommand`, `UpdateTaskCommand`) |
52: | **3-5. Query** | `V + N + Query` | 動詞 + 名詞 + Query (例如：`GetTaskQuery`, `ListTaskQuery`) |
53: | **3-6. Dto** | `N + Gotten/Searched + Result` | 名詞 + 查詢類分詞(如Gotten, Searched) + Result (例如：`TaskGottenResult`, `TaskSearchedResult`) |
54: | **3-7. Projection** | `N + Gotten/Searched + View` | 名詞 + 查詢類分詞(如Gotten, Searched) + View (例如：`TaskGottenView`, `TaskSearchedView`) |
55: | **3-8. Port** | `in` : `... + UseCase`<br>`out` : `... + Port` | Inbound Port 一律以 `UseCase` 結尾 (如`CreateTaskUseCase`)；Outbound Port 一律以 `Port` 結尾 (如`TaskRepositoryPort`) |
56: | **3-9. Adapter** | `in` : `... + ApplicationService` (沒有 CQRS) 或 `... + CommandService` / `... + QueryService` (CQRS)<br>`out` : `... + Adapter` | Inbound Adapter 實作用於 `ApplicationService` / `CommandService` / `QueryService` (如`TaskApplicationService`)；Outbound Adapter 一律以 `Adapter` 結尾 (如`TaskRepositoryAdapter`) |
57: 
58: ## 4. 程式碼註解與說明規範 (Javadoc & Comments)
59: - **極致清晰的註解與 Javadoc**：專案內的所有介面、類別、實體物件、DTO、Port、Adapter 以及其內部的重要業務方法，**皆必須加上清晰的 Javadoc 或註解**。
60: - **說明業務與架構職責**：Javadoc 中需明確說明該類別或方法在 DDD / 六角形架構中的具體業務職責、參數與回傳值的意義（如 `@param`, `@return`、說明發布的領域事件、異常例外等），確保任何開發者或 AI 代理都能迅速理解該邏輯的角色與意圖。
61: 
62: ## 5. 測試規範與 UseCase 測試要求 (Testing & Verification)
63: - **UseCase 必須涵蓋測試 (Mandatory UseCase Tests)**：每一個實作的 `UseCase` 業務功能（如 `CreateTaskUseCase`, `UpdateTaskUseCase` 等），**必須要撰寫對應的單元測試或整合測試 (Unit / Integration Tests)**，覆蓋測試正常業務流程、錯誤異常分支、事件發布與例外拋出（如 `TaskNotFoundException` 等），確保每個 UseCase 功能的正確性。
64: - **BDD 與 Gherkin 測試註解規範**：每個測試方法的上方，**必須使用 Gherkin 語法 (Feature, Scenario, Given-When-Then) 撰寫 Javadoc**，清楚描述該測試驗證的業務情境與預期行為，作為測試代碼與業務需求的溝通橋樑。
65: - **測試執行驗證**：所有修改程式碼後，必須確保本地端單元測試全數通過 (`BUILD SUCCESS`)。
66: 
67: - **Import 規範**：撰寫程式時請撰寫特定的 import 即可，不用將整個類的 package 路徑都寫上 (Avoid fully qualified class names inline).
The above content shows the entire, complete file contents of the requested file.
