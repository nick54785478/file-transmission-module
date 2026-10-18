# 傳輸模組機密防護與金鑰維護手冊 (SECURITY_MAINTENANCE_GUIDE.md)

本文件專為**維運工程師 (DevOps / SRE)**、**後端開發人員**與**資安稽核人員**撰寫，詳細說明 `file-transmission-module` 內建的機密防護機制、AES-GCM 加密架構、外部化金鑰配置階層、各環境部署指引、金鑰輪換 (Key Rotation) 作業流程與故障排查。

> 📘 **相關重要文件：**
> * 欲深入了解「遠端主機帳密、SSH 私鑰與內部 AES-GCM 金鑰的 3 類角色定位與端到端生命週期」，請參閱：[CREDENTIAL_AND_AES_KEY_ARCHITECTURE_GUIDE.md](file:///D:/桌面/ModuleWorkSpace/file-transmission-module/doc/maintainance/CREDENTIAL_AND_AES_KEY_ARCHITECTURE_GUIDE.md)

---

## 目錄
- [一、 機密防護機制核心架構](#一-機密防護機制核心架構)
  - [1.1 為什麼需要金鑰外部化？(資安盲點排除)](#11-為什麼需要金鑰外部化資安盲點排除)
  - [1.2 加密規格與安全特性](#12-加密規格與安全特性)
  - [1.3 日誌脫敏防洩機制 (Data Masking)](#13-日誌脫敏防洩機制-data-masking)
- [二、 金鑰解析優先層級 (Hierarchy)](#二-金鑰解析優先層級-hierarchy)
- [三、 各環境部署與金鑰配置指南](#三-各環境部署與金鑰配置指南)
  - [3.1 Docker / 容器化環境 (推薦)](#31-docker--容器化環境-推薦)
  - [3.2 Kubernetes (K8s Secret)](#32-kubernetes-k8s-secret)
  - [3.3 傳統 Linux / Windows 伺服器 (Systemd / JVM 參數)](#33-傳統-linux--windows-伺服器-systemd--jvm-參數)
  - [3.4 本機開發環境 (Local Development)](#34-本機開發環境-local-development)
- [四、 密文生成工具與操作範例](#四-密文生成工具與操作範例)
  - [4.1 如何將明文密碼加密為 ENC(...) 格式？](#41-如何將明文密碼加密為-enc-格式)
  - [4.2 API 請求實際調用範例](#42-api-請求實際調用範例)
- [五、 金鑰輪換作業流程 (Key Rotation Procedure)](#五-金鑰輪換作業流程-key-rotation-procedure)
  - [5.1 定期輪換標準程序 (SOP)](#51-定期輪換標準程序-sop)
  - [5.2 緊急洩漏因應程序](#52-緊急洩漏因應程序)
- [六、 常見問題與故障排查 (Troubleshooting FAQ)](#六-常見問題與故障排查-troubleshooting-faq)

---

## 一、 機密防護機制核心架構

### 1.1 為什麼需要金鑰外部化？(資安盲點排除)
若將加密金鑰寫死（Hardcoded）在程式原始碼中，會引發以下重大資安風險：
* **原始碼外洩即代表防護崩潰 (CWE-321 / CWE-798 弱點)**：任何人取得 Git 權限或反編譯 Jar 檔，即可輕易獲取金鑰並還原密碼。
* **無法動態輪換金鑰 (Key Rotation)**：更換金鑰必須重新修改程式碼、重新編譯並上線發版。
* **環境隔離失效**：開發 (Dev)、測試 (UAT) 與正式 (Prod) 環境共用金鑰，違反縱深防禦與權限隔離原則。
* **法規合規未通過**：無法通過 PCI-DSS、ISO 27001 與企業資安靜態掃描 (SonarQube/Fortify/Checkmarx) 稽核。

本模組透過**外部化配置 (Externalized Configuration)**，使原始碼內無任何真實正式環境金鑰，由各環境部署設施動態注入。

### 1.2 加密規格與安全特性
本模組在 [`CredentialCipherPort`](file:///d:/桌面/ModuleWorkSpace/file-transmission-module/src/main/java/com/example/demo/application/port/out/CredentialCipherPort.java) 與 [`CredentialCipherAdapter`](file:///d:/桌面/ModuleWorkSpace/file-transmission-module/src/main/java/com/example/demo/infrastructure/adapter/CredentialCipherAdapter.java) 中提供純 Java 標準高強度加解密：

| 項目 | 規格與演算法 | 說明 |
| :--- | :--- | :--- |
| **對稱加密演算法** | **AES-GCM (128-bit)** | 工業標準 AEAD (Authenticated Encryption with Associated Data)，自帶防竄改驗證標籤 |
| **轉換模式** | `AES/GCM/NoPadding` | 杜絕 Padding Oracle 密碼學攻擊 |
| **初始化向量 (IV)** | 12 Bytes 隨機 IV | 每次加密透過 `SecureRandom` 動態生成，**相同密碼兩次加密密文完全不同**，徹底杜絕重放與模式分析攻擊 |
| **金鑰衍生機制 (KDF)** | **SHA-256 Digest** | 支援任意長度字串作為密鑰，自動透過 SHA-256 均勻分散並衍生為 128-bit AES 金鑰，不會因字串長度引發例外 |
| **密文標記化格式** | `ENC(<Base64_IV_Cipher>)` | 格式範例：`ENC(9xVbO...uQ==)`，清晰標記已加密狀態 |
| **無感解密與向下相容**| 自動偵測 | 若傳入 `ENC(...)` 則自動解密；若傳入一般明文則原樣保留，既有系統 100% 向下相容 |
| **領域模型純粹性** | Pure Value Object | [`TransmissionServerConfig`](file:///d:/桌面/ModuleWorkSpace/file-transmission-module/src/main/java/com/example/demo/application/shared/vo/TransmissionServerConfig.java) 為純粹不可變實質物件，零靜態相依 |

### 1.3 架構分層與依賴注入 (Clean Architecture)
本系統嚴格遵循六角形架構 (Ports & Adapters) 與依賴反轉原則：
1. **配置層 (`config/properties/`)**：由 [`TransmissionSecurityProperties`](file:///d:/桌面/ModuleWorkSpace/file-transmission-module/src/main/java/com/example/demo/config/properties/TransmissionSecurityProperties.java) 綁定 `transmission.security.*` 屬性。
2. **配置裝配 (`config/config/`)**：由 [`FileTransmissionConfig`](file:///d:/桌面/ModuleWorkSpace/file-transmission-module/src/main/java/com/example/demo/config/config/FileTransmissionConfig.java) 依據屬性註冊 `CredentialCipherPort` 實例 Bean。
3. **應用層埠 (`application/port/out/`)**：定義抽象輸出埠 [`CredentialCipherPort`](file:///d:/桌面/ModuleWorkSpace/file-transmission-module/src/main/java/com/example/demo/application/port/out/CredentialCipherPort.java)。
4. **基礎設施適配器 (`infrastructure/adapter/`)**：由 [`CredentialCipherAdapter`](file:///d:/桌面/ModuleWorkSpace/file-transmission-module/src/main/java/com/example/demo/infrastructure/adapter/CredentialCipherAdapter.java) 實作加解密邏輯，完全無靜態全域狀態。
5. **應用層服務 (`application/service/`)**：[`FileTransmissionCommandService`](file:///d:/桌面/ModuleWorkSpace/file-transmission-module/src/main/java/com/example/demo/application/service/FileTransmissionCommandService.java) 注入 `CredentialCipherPort`，在執行傳輸前調度解密。

### 1.4 日誌脫敏防洩機制 (Data Masking)
為防止密碼或 SSH 私鑰在系統日誌 (Logback / ELK / Console) 中意外洩漏，系統全面覆寫了以下核心物件的 `toString()`：
* [`TransmissionServerConfig`](file:///d:/桌面/ModuleWorkSpace/file-transmission-module/src/main/java/com/example/demo/application/shared/vo/TransmissionServerConfig.java)：連線密碼與 Passphrase 遮罩為 `******`，私鑰內容遮罩為 `[CONFIGURED]`。
* [`UploadFileResource`](file:///d:/桌面/ModuleWorkSpace/file-transmission-module/src/main/java/com/example/demo/presentation/resource/in/UploadFileResource.java)、[`DownloadFileResource`](file:///d:/桌面/ModuleWorkSpace/file-transmission-module/src/main/java/com/example/demo/presentation/resource/in/DownloadFileResource.java)、[`SyncDirectoryResource`](file:///d:/桌面/ModuleWorkSpace/file-transmission-module/src/main/java/com/example/demo/presentation/resource/in/SyncDirectoryResource.java)：請求載體全面遮罩。

---

## 二、 金鑰解析與配置優先層級

在 Spring Boot 框架下，`TransmissionSecurityProperties` 的金鑰讀取遵循標準 Spring 外部化配置順序：

```
[優先級 1] 命令列引數 / JVM 參數 (--transmission.security.secret-key=... 或 -D...)
    ↓ (若為空)
[優先級 2] 作業系統環境變數 (TRANSMISSION_SECURITY_SECRET_KEY 或 TRANSMISSION_SECRET_KEY)
    ↓ (若為空)
[優先級 3] 外部或特定 Profile 設定檔 (application-prod.properties: transmission.security.secret-key)
    ↓ (若為空)
[優先級 4] 內部預設備援金鑰 (DEFAULT_DEV_KEY，容器啟動時輸出 WARN 資安警告)
```

> [!WARNING]
> 正式環境 (Production) **嚴禁使用優先級 4 備援金鑰**！若系統啟動時日誌出現 `[Security] 未檢測到自訂傳輸金鑰設定...` 警告，維運人員必須立即配置 `transmission.security.secret-key`。

---

## 三、 各環境部署與金鑰配置指南

### 3.1 Docker / 容器化環境 (推薦)
在 `docker-compose.yml` 或 `docker run` 中透過環境變數注入金鑰：

#### 方法 A：使用 `docker run` 命令列
```bash
docker run -d \
  --name file-transmission-service \
  -p 8080:8080 \
  -e TRANSMISSION_SECRET_KEY="ProdSecKey#Company2026!X9qL" \
  file-transmission-module:latest
```

#### 方法 B：在 `docker-compose.yml` 配置
```yaml
version: '3.8'
services:
  file-transmission-module:
    image: file-transmission-module:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      # 建議由宿主機環境變數注入，避免直接寫入 docker-compose 檔案
      - TRANSMISSION_SECRET_KEY=${PROD_TRANSMISSION_SECRET_KEY}
```

---

### 3.2 Kubernetes (K8s Secret)
在雲原生 K8s 環境中，強烈建議將金鑰存放於 Kubernetes Secret 物件中：

#### 步驟 1：建立 K8s Secret
```bash
kubectl create secret generic transmission-security-secret \
  --from-literal=secret-key='EnterpriseProdAESKey2026!#UltraSecure' \
  -n production
```

#### 步驟 2：在 Deployment 中掛載為環境變數
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: file-transmission-deployment
  namespace: production
spec:
  replicas: 2
  template:
    spec:
      containers:
        - name: file-transmission-app
          image: myregistry/file-transmission-module:1.0.0
          env:
            - name: TRANSMISSION_SECRET_KEY
              valueFrom:
                secretKeyRef:
                  name: transmission-security-secret
                  key: secret-key
```

---

### 3.3 傳統 Linux / Windows 伺服器 (Systemd / JVM 參數)

#### 方法 A：Linux Systemd Service
在 `/etc/systemd/system/file-transmission.service` 中配置 `Environment`：
```ini
[Unit]
Description=File Transmission Module Application
After=network.target

[Service]
Type=simple
User=appuser
Environment="TRANSMISSION_SECRET_KEY=LinuxProdServerSecret_2026!"
ExecStart=/usr/bin/java -jar /opt/app/file-transmission-module.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

#### 方法 B：Java JVM 啟動參數
```bash
java -Dtransmission.secret.key="LinuxProdServerSecret_2026!" -jar file-transmission-module.jar
```

---

### 3.4 本機開發環境 (Local Development)
開發人員在本機開發或跑單元測試時，有以下幾種方式：
1. **完全不配置**：系統會自動採用內部 `DEV_FALLBACK_KEY`，確保本機開發與測試能直接運行無阻。
2. **在 IDE (IntelliJ IDEA / VSCode) 設置環境變數**：
   * 在 Run/Debug Configuration 的 `Environment variables` 加入：
     `TRANSMISSION_SECRET_KEY=MyLocalTestSecretKey123`
3. **在 `application-dev.properties` 設置**：
   ```properties
   transmission.security.secret-key=MyLocalDevCustomKey
   ```

---

## 四、 密文生成工具與操作範例

### 4.1 如何將明文密碼加密為 ENC(...) 格式？
本模組提供了純 Java 的靜態工具方法，可直接用於離線加密或撰寫獨立的 CLI 工具：

```java
import com.example.demo.application.shared.security.TransmissionCipher;

public class EncryptTool {
    public static void main(String[] args) {
        // 1. (選填) 若有自訂環境金鑰，可先設定；若未設定則採用目前環境之金鑰
        // TransmissionCipher.setSecretKey("MyProdCorp@Secret#2026!");

        String plainPassword = "SuperConfidentialPassword123";
        String encryptedToken = TransmissionCipher.encrypt(plainPassword);

        System.out.println("原始明文: " + plainPassword);
        System.out.println("產生密文: " + encryptedToken);
        // 輸出範例：ENC(wM9q1Rx9Kz...==)
    }
}
```

---

### 4.2 API 請求實際調用範例

#### 範例 1：使用加密密文 `ENC(...)` 上傳檔案
外部系統呼叫 API 時，只需將密碼欄位替換為 `ENC(...)` 密文，後端服務會**自動於記憶體中解密**，完全無需手動解密：

```http
POST /api/transmission/upload HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "host": "sftp.bank.com.tw",
  "port": 22,
  "protocol": "SFTP",
  "username": "fintech_batch_user",
  "password": "ENC(q9L1vP8zN...==)",
  "localFilePath": "D:/export/settlement_20261017.csv",
  "remotePath": "/inbound/settlement",
  "filename": "settlement_20261017.csv"
}
```

#### 範例 2：SFTP SSH 私鑰 + 私鑰保護密碼 (Passphrase) 加密呼叫
```http
POST /api/transmission/upload HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "host": "sftp.corp.local",
  "port": 22,
  "protocol": "SFTP",
  "username": "deploy_agent",
  "privateKey": "/etc/secrets/sftp_id_rsa",
  "passphrase": "ENC(8bNm3Xy...==)",
  "localFilePath": "D:/data/backup.zip",
  "remotePath": "/backup"
}
```

---

## 五、 金鑰輪換作業流程 (Key Rotation Procedure)

### 5.1 定期輪換標準程序 (SOP)
當依循資安政策定期（如每半年或每年）輪換主金鑰時，請依以下步驟進行：

```
[步驟 1: 生成新密鑰] ──> [步驟 2: 重加密密文] ──> [步驟 3: 更新配置並重啟] ──> [步驟 4: 測試驗證]
```

1. **生成全新高強度密鑰 (New Key)**：
   * 密鑰長度建議至少 24 ~ 32 個字元，包含大小寫英文字母、數字與特殊符號。
   * 範例：`TxSec#2026_Q4_K8s_Prod!v7$m`。
2. **批次重新加密連線密碼**：
   * 呼叫 [`TransmissionCipher.setSecretKey(newKey)`](file:///d:/桌面/ModuleWorkSpace/file-transmission-module/src/main/java/com/example/demo/application/shared/security/TransmissionCipher.java) 將所有遠端伺服器之密碼與 Passphrase 重新產生為新 `ENC(...)` 密文。
3. **更新配置中心 / K8s Secret / 環境變數**：
   * 將 `TRANSMISSION_SECRET_KEY` 替換為新密鑰。
   * 將系統中保存之連線設定（如排程設定或呼叫端傳入之密文）更新為新產生的 `ENC(...)` 密文。
4. **重新載入服務並驗證傳輸**：
   * 重新啟動微服務實例。
   * 呼叫任一傳輸 API 測試連線是否成功。

---

### 5.2 緊急洩漏因應程序
若懷疑正式環境主金鑰遭到未授權存取或洩漏：
1. **立即變更遠端伺服器密碼**：
   * 優先變更所有 SFTP / FTP 目標主機上的帳號登入密碼及 SSH 私鑰。
2. **產出全新主金鑰並更新 K8s Secret / 環境變數**。
3. **將變更後的密碼以新金鑰加密產出全新 `ENC(...)` 密文**。
4. **滾動更新 (Rolling Restart) 所有微服務 Pod**。
5. **檢閱稽核日誌**確認未有異常傳輸記錄。

---

## 六、 常見問題與故障排查 (Troubleshooting FAQ)

### Q1: 啟動時出現錯誤 `機密密碼解密失敗 (密文毀損或金鑰不符)`？
* **原因 1：金鑰不匹配**。該 `ENC(...)` 密文是在使用金鑰 A 時產生的，但當前服務運行時所載入的金鑰為金鑰 B。
  * **解決方式**：請確認目前伺服器載入的 `TRANSMISSION_SECRET_KEY` 是否與加密時所用的金鑰完全一致（注意前後是否有隱藏空格或換行符號）。
* **原因 2：密文被手動截斷或改動**。Base64 字串中若缺少字元，將無法通過 AES-GCM 的驗證標籤（Tag）檢查。
  * **解決方式**：重新加密並複製完整的 `ENC(...)` 字串。

### Q2: 系統啟動日誌出現警告：`[Security] 未檢測到外部傳輸金鑰設定，目前使用開發用預設金鑰！`？
* **說明**：表示系統在 Spring 配置、系統環境變數與 JVM 參數中均未找到任何外部自訂金鑰，因此啟用了內建備援。
* **處理方式**：
  * 若在**本機開發環境**：此警告為正常提示，可忽略並正常開發。
  * 若在**正式或測試環境**：請務必透過作業系統環境變數 `TRANSMISSION_SECRET_KEY` 配置自訂密鑰。

### Q3: 傳入一般的明文密碼（未加密）還能正常運作嗎？
* **可以！完全支援**。
* `TransmissionCipher.decryptIfEncrypted()` 會主動辨識字串前綴與後綴。若不是以 `ENC(` 開頭且以 `)` 結尾，系統會認定為明文直接透傳，不會發生錯誤，具備 100% 向下相容性。

### Q4: 如何確認 Log 日誌中是否有密碼外洩？
* 本模組已在所有 Request Resource 與 `TransmissionServerConfig` 物件中實作遮罩防護。
* 您可以搜尋 Logback 日誌檔案：若輸出連線設定或請求內容，密碼將顯示為 `password=******`，私鑰將顯示為 `privateKey=[CONFIGURED]`。若出現任何明文，請向開發團隊回報。
