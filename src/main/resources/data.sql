-- ==============================================================================
-- 檔案傳輸模組 (File Transmission Module) 測試資料初始化腳本 (data.sql)
--
-- 說明：
-- 1. 於系統啟動時由 Spring Boot (spring.sql.init.mode=always) 自動執行。
-- 2. 對應 docker-compose/docker-compose.yml 所宣告的 3 台本機模擬傳輸伺服器：
--    - mock-sftp (SFTP, Port 2222)
--    - mock-ftp  (FTP,  Port 2121)
--    - mock-ftps (FTPS, Port 2122)
-- 3. 採用 WHERE NOT EXISTS 語法確保執行之「冪等性」(重複執行不報錯、不重複寫入)。
-- 4. 密碼欄位支援明文 'password' 或 ENC(...) 密文 (模組具備向下相容自動判定機制)。
-- ==============================================================================

-- 1. 若資料庫已存在記錄，更新說明文字並將明文密碼轉為 AES-GCM ENC(...) 密文 (落盤加密)
UPDATE server_credentials
SET description = N'Docker 本機模擬 SFTP 伺服器 (Port: 2222, 帳密: tester / password)',
    encrypted_password = 'ENC(pni0IA3kZU+rMPecevelg0aWEFag6VaAV4NvK3CBNyQEu2Fn)',
    updated_at = CURRENT_TIMESTAMP
WHERE alias = 'mock-sftp';

UPDATE server_credentials
SET description = N'Docker 本機模擬 FTP 伺服器 (Port: 2121, 被動埠: 21100-21110, 帳密: tester / password)',
    encrypted_password = 'ENC(pni0IA3kZU+rMPecevelg0aWEFag6VaAV4NvK3CBNyQEu2Fn)',
    updated_at = CURRENT_TIMESTAMP
WHERE alias = 'mock-ftp';

UPDATE server_credentials
SET description = N'Docker 本機模擬 FTPS (TLS Explicit) 伺服器 (Port: 2122, 被動埠: 21120-21130, 帳密: tester / password)',
    encrypted_password = 'ENC(pni0IA3kZU+rMPecevelg0aWEFag6VaAV4NvK3CBNyQEu2Fn)',
    updated_at = CURRENT_TIMESTAMP
WHERE alias = 'mock-ftps';

-- 2. 若尚未存在則執行新增 (密碼採 AES-GCM 密文落盤，字串前綴加上 N'...' 支援 Unicode)
-- SFTP 模擬伺服器 (Port: 2222, 帳密: tester / password)
INSERT INTO server_credentials (
    id, alias, host, port, protocol, username,
    encrypted_password, encrypted_private_key, encrypted_passphrase,
    description, is_active, created_at, updated_at
)
SELECT
    'cred-mock-sftp-001', 'mock-sftp', '127.0.0.1', 2222, 'SFTP', 'tester',
    'ENC(pni0IA3kZU+rMPecevelg0aWEFag6VaAV4NvK3CBNyQEu2Fn)', NULL, NULL,
    N'Docker 本機模擬 SFTP 伺服器 (Port: 2222, 帳密: tester / password)', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM server_credentials WHERE alias = 'mock-sftp'
);

-- FTP 模擬伺服器 (Port: 2121, 被動埠: 21100-21110, 帳密: tester / password)
INSERT INTO server_credentials (
    id, alias, host, port, protocol, username,
    encrypted_password, encrypted_private_key, encrypted_passphrase,
    description, is_active, created_at, updated_at
)
SELECT
    'cred-mock-ftp-002', 'mock-ftp', '127.0.0.1', 2121, 'FTP', 'tester',
    'ENC(pni0IA3kZU+rMPecevelg0aWEFag6VaAV4NvK3CBNyQEu2Fn)', NULL, NULL,
    N'Docker 本機模擬 FTP 伺服器 (Port: 2121, 被動埠: 21100-21110, 帳密: tester / password)', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM server_credentials WHERE alias = 'mock-ftp'
);

-- FTPS (TLS Explicit) 模擬伺服器 (Port: 2122, 被動埠: 21120-21130, 帳密: tester / password)
INSERT INTO server_credentials (
    id, alias, host, port, protocol, username,
    encrypted_password, encrypted_private_key, encrypted_passphrase,
    description, is_active, created_at, updated_at
)
SELECT
    'cred-mock-ftps-003', 'mock-ftps', '127.0.0.1', 2122, 'FTPS', 'tester',
    'ENC(pni0IA3kZU+rMPecevelg0aWEFag6VaAV4NvK3CBNyQEu2Fn)', NULL, NULL,
    N'Docker 本機模擬 FTPS (TLS Explicit) 伺服器 (Port: 2122, 被動埠: 21120-21130, 帳密: tester / password)', 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM server_credentials WHERE alias = 'mock-ftps'
);
