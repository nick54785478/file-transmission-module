package com.example.demo.application.shared.security;

import com.example.demo.application.port.out.CredentialCipherPort;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.example.demo.config.properties.TransmissionSecurityProperties;
import com.example.demo.infrastructure.adapter.CredentialCipherAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 傳輸機密加密工具與連線資訊脫敏單元測試 (TransmissionCipherTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 點)：
 * 1. 涵蓋正常業務流程、邊界條件與例外拋出；
 * 2. 測試方法均使用 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then) 標註。
 * </p>
 */
class TransmissionCipherTest {

    /**
     * Feature: 機密資料加密機制
     * Scenario: 將明文字串成功以 AES-GCM 加密為 ENC(...) 格式，且每次產生的隨機 IV 確保密文相異
     * Given: 準備一組機密密碼明文
     * When: 呼叫 TransmissionCipher.encrypt 兩次
     * Then: 兩次均產出以 ENC( 開頭與 ) 結尾之密文，且兩次密文不相同
     */
    @Test
    @DisplayName("情境：明文密碼成功加密為隨機 IV 之 ENC(...) 格式")
    void testEncryptProducesEncPrefixAndRandomIv() {
        String plain = "MySecretP@ssw0rd_2026";

        String cipher1 = TransmissionCipher.encrypt(plain);
        String cipher2 = TransmissionCipher.encrypt(plain);

        assertNotNull(cipher1);
        assertNotNull(cipher2);
        assertTrue(cipher1.startsWith("ENC("));
        assertTrue(cipher1.endsWith(")"));
        assertTrue(cipher2.startsWith("ENC("));
        assertTrue(cipher2.endsWith(")"));
        assertNotEquals(cipher1, cipher2);
    }

    /**
     * Feature: 機密資料解密機制
     * Scenario: 成功將 ENC(...) 密文正確解密回原始明文
     * Given: 一組經 TransmissionCipher.encrypt 加密之 ENC(...) 密文字串
     * When: 呼叫 TransmissionCipher.decryptIfEncrypted 進行解密
     * Then: 回傳值必須與原始明文字串完全相符
     */
    @Test
    @DisplayName("情境：成功將 ENC(...) 格式密文還原為原始明文")
    void testDecryptIfEncryptedWithValidCipher() {
        String original = "Database#SecurePassword$999";
        String cipher = TransmissionCipher.encrypt(original);

        String decrypted = TransmissionCipher.decryptIfEncrypted(cipher);

        assertEquals(original, decrypted);
    }

    /**
     * Feature: 明文無感透傳機制
     * Scenario: 傳入非 ENC(...) 標記之一般明文或空值時，原值直接透傳返回
     * Given: 一般未加密之明文字串、空字串與 null
     * When: 呼叫 TransmissionCipher.decryptIfEncrypted
     * Then: 回傳原字串內容或空值，不發生錯誤亦不異動內容
     */
    @Test
    @DisplayName("情境：未加密之明文或空字串直接原值回傳")
    void testDecryptIfEncryptedWithPlainTextAndNull() {
        String plain = "RegularPlainTextPassword";

        assertEquals(plain, TransmissionCipher.decryptIfEncrypted(plain));
        assertEquals("", TransmissionCipher.decryptIfEncrypted(""));
        assertNull(TransmissionCipher.decryptIfEncrypted(null));
    }

    /**
     * Feature: 異常密文防禦機制
     * Scenario: 傳入被竄改或損毀之 ENC(...) 密文時拋出 IllegalArgumentException
     * Given: 一組偽造或長度不足之 ENC(...) 字串
     * When: 呼叫 TransmissionCipher.decryptIfEncrypted
     * Then: 拋出 IllegalArgumentException 例外，杜絕不合規資料
     */
    @Test
    @DisplayName("情境：解密毀損或非法之 ENC(...) 密文時拋出 IllegalArgumentException")
    void testDecryptIfEncryptedWithCorruptedCipherThrowsException() {
        String corruptedCipher = "ENC(ABC123InvalidBase64Content)";

        assertThrows(IllegalArgumentException.class, () -> TransmissionCipher.decryptIfEncrypted(corruptedCipher));
    }

    /**
     * Feature: 密文格式判斷檢驗
     * Scenario: 正確辨識字串是否符合 ENC(...) 密文規範
     * Given: 有效密文、一般明文與空值
     * When: 呼叫 TransmissionCipher.isEncrypted
     * Then: 僅對合規的 ENC(...) 格式回傳 true，其餘回傳 false
     */
    @Test
    @DisplayName("情境：正確判斷字串是否為已加密格式")
    void testIsEncrypted() {
        assertTrue(TransmissionCipher.isEncrypted("ENC(validCipherToken)"));
        assertFalse(TransmissionCipher.isEncrypted("plainText"));
        assertFalse(TransmissionCipher.isEncrypted("ENC("));
        assertFalse(TransmissionCipher.isEncrypted(""));
        assertFalse(TransmissionCipher.isEncrypted(null));
    }

    /**
     * Feature: 傳輸伺服器連線配置透過 CredentialCipherPort 解密機制
     * Scenario: 當連線配置中傳入 ENC(...) 密碼與私鑰 Passphrase 時，透過 CredentialCipherPort.decryptServerConfig 解密為明文
     * Given: 使用 CredentialCipherAdapter 加密之密碼與 Passphrase，並建立包含密文之 TransmissionServerConfig
     * When: 呼叫 cipherPort.decryptServerConfig(config)
     * Then: 回傳之 effectiveConfig 中 getPassword() 與 getPassphrase() 為解密後的明文
     */
    @Test
    @DisplayName("情境：CredentialCipherPort 成功解密 TransmissionServerConfig 之 ENC(...) 密碼與私鑰通行碼")
    void testCredentialCipherPortDecryptServerConfig() {
        CredentialCipherPort cipherAdapter = new CredentialCipherAdapter("CustomTestSecretKey123");
        String plainPassword = "SecretSftpPassword123";
        String plainPassphrase = "MyKeyPassphrase456";
        String encPassword = cipherAdapter.encrypt(plainPassword);
        String encPassphrase = cipherAdapter.encrypt(plainPassphrase);

        TransmissionServerConfig rawConfig = new TransmissionServerConfig(
                "sftp.example.com",
                22,
                TransmissionProtocol.SFTP,
                "sftp_user",
                encPassword,
                10000,
                true,
                false,
                "D:/keys/id_rsa",
                encPassphrase
        );

        // 原始 config 保留未解密密文 (符合 Pure VO 職責)
        assertEquals(encPassword, rawConfig.getPassword());
        assertEquals(encPassphrase, rawConfig.getPassphrase());

        // 透過 cipherPort 解密產生全新不可變配置實例
        TransmissionServerConfig decryptedConfig = cipherAdapter.decryptServerConfig(rawConfig);

        assertEquals(plainPassword, decryptedConfig.getPassword());
        assertEquals(plainPassphrase, decryptedConfig.getPassphrase());
        assertTrue(decryptedConfig.hasPrivateKey());
        assertTrue(decryptedConfig.hasPassphrase());
    }

    /**
     * Feature: 連線配置日誌脫敏遮罩防護 (機密盲點修復驗證)
     * Scenario: 呼叫 TransmissionServerConfig.toString 時，密碼、私鑰密碼與私鑰內容均被遮罩脫敏
     * Given: 包含機密明文密碼、私鑰檔案路徑與 Passphrase 之 TransmissionServerConfig 物件
     * When: 呼叫 config.toString() 輸出字串
     * Then: 輸出的字串中密碼與 Passphrase 顯示為 ******，私鑰顯示為 [CONFIGURED]，絕不洩漏機密
     */
    @Test
    @DisplayName("情境：toString 覆寫嚴密遮罩脫敏密碼與私鑰，防止日誌意外洩密")
    void testTransmissionServerConfigToStringMasksSensitiveCredentials() {
        TransmissionServerConfig config = new TransmissionServerConfig(
                "sftp.example.com",
                22,
                TransmissionProtocol.SFTP,
                "admin",
                "SuperConfidentialPassword!",
                15000,
                true,
                false,
                "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA...\n-----END RSA PRIVATE KEY-----",
                "KeyPassphrase789"
        );

        String result = config.toString();

        assertFalse(result.contains("SuperConfidentialPassword!"));
        assertFalse(result.contains("KeyPassphrase789"));
        assertFalse(result.contains("BEGIN RSA PRIVATE KEY"));
        assertTrue(result.contains("password=******"));
        assertTrue(result.contains("passphrase=******"));
        assertTrue(result.contains("privateKey=[CONFIGURED]"));
    }

    /**
     * Feature: 連線配置靜態工廠方法與金鑰旗標判定
     * Scenario: 透過 ofKey 工廠方法建立金鑰認證之配置物件，並正確判定金鑰狀態
     * Given: 私鑰路徑與選填的 Passphrase
     * When: 透過 TransmissionServerConfig.ofKey 建立實例
     * Then: hasPrivateKey() 為 true，hasPassphrase() 依是否提供 Passphrase 正確判定
     */
    @Test
    @DisplayName("情境：ofKey 工廠方法與金鑰狀態判定運作正常")
    void testTransmissionServerConfigOfKey() {
        TransmissionServerConfig configWithPassphrase = TransmissionServerConfig.ofKey(
                "sftp.corp.local",
                22,
                TransmissionProtocol.SFTP,
                "devops",
                "D:/keys/sftp_prod.pem",
                "Passphrase#123"
        );

        assertTrue(configWithPassphrase.hasPrivateKey());
        assertTrue(configWithPassphrase.hasPassphrase());
        assertEquals("Passphrase#123", configWithPassphrase.getPassphrase());

        TransmissionServerConfig configWithoutPassphrase = TransmissionServerConfig.ofKey(
                "sftp.corp.local",
                22,
                TransmissionProtocol.SFTP,
                "devops",
                "D:/keys/sftp_prod.pem",
                null
        );

        assertTrue(configWithoutPassphrase.hasPrivateKey());
        assertFalse(configWithoutPassphrase.hasPassphrase());
        assertNull(configWithoutPassphrase.getPassphrase());
    }

    /**
     * Feature: 外部自訂金鑰適配器實例隔離機制
     * Scenario: 設定不同金鑰之 CredentialCipherAdapter 實例彼此嚴格隔離，無法交互解密
     * Given: 建立兩個配置不同金鑰之適配器實例 AdapterA 與 AdapterB
     * When: 使用 AdapterA 加密明文，並嘗試使用 AdapterB 進行解密
     * Then: 拋出 IllegalArgumentException 解密失敗例外，證明純實例化金鑰防護具備完整隔離性
     */
    @Test
    @DisplayName("情境：純實例化適配器金鑰隔離完整，無靜態狀態污染風險")
    void testCustomKeySettingAndCrossKeyIsolation() {
        String plain = "EnterpriseConfidentialData#2026";

        CredentialCipherPort adapterA = new CredentialCipherAdapter("ProductionCustomSecretKey_AAA");
        CredentialCipherPort adapterB = new CredentialCipherAdapter("DifferentSecretKey_BBB");

        String cipherA = adapterA.encrypt(plain);
        assertEquals(plain, adapterA.decryptIfEncrypted(cipherA));

        // 驗證 adapterB 無法解密 adapterA 產生之密文 (跨金鑰嚴密隔離)
        assertThrows(IllegalArgumentException.class, () -> adapterB.decryptIfEncrypted(cipherA));
    }

    /**
     * Feature: 安全配置屬性載體綁定測試
     * Scenario: 驗證 TransmissionSecurityProperties 之預設值與自訂金鑰綁定正常
     * Given: 建立 TransmissionSecurityProperties 實例
     * When: 檢查預設值與進行 setSecretKey
     * Then: 正確判定 isDefaultOrEmpty 與回傳指定金鑰
     */
    @Test
    @DisplayName("情境：TransmissionSecurityProperties 屬性載體綁定運作正常")
    void testTransmissionSecurityProperties() {
        TransmissionSecurityProperties properties = new TransmissionSecurityProperties();
        assertTrue(properties.isDefaultOrEmpty());
        assertEquals(TransmissionSecurityProperties.DEFAULT_DEV_KEY, properties.getSecretKey());

        properties.setSecretKey("CustomEnvSecretKey_999");
        assertFalse(properties.isDefaultOrEmpty());
        assertEquals("CustomEnvSecretKey_999", properties.getSecretKey());
    }
}
