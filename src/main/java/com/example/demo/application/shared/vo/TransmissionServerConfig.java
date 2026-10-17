package com.example.demo.application.shared.vo;

import java.io.Serializable;

/**
 * 傳輸伺服器連線配置數值物件 (TransmissionServerConfig - Value Object Record)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>應用層共用數值物件 (Shared VO Record)，封裝遠端伺服器 (FTP / FTPS / SFTP) 連線驗證之完整不可變配置。</li>
 *   <li>純粹領域實質物件 (Pure Value Object)：零外部框架依賴、維持完全不可變性 (Immutability)。</li>
 *   <li>包含主機 IP/網址、連接埠、帳號密碼、傳輸逾時時間 (timeoutMs)、被動模式 (Passive Mode) 以及嚴格金鑰檢查設定。</li>
 *   <li><b>安全性強化機制：</b>
 *     <ul>
 *       <li>支援 SSH 私鑰身分認證 (<code>privateKey</code> 與可選 <code>passphrase</code>)。</li>
 *       <li><b>覆寫 {@link #toString()} 實現日誌脫敏：</b> 遮蔽密碼與私鑰，防止機密在日誌系統輸出洩漏。</li>
 *       <li>提供 {@link #withDecryptedCredentials(String, String)} 供應用層解密後衍生新實例。</li>
 *     </ul>
 *   </li>
 *   <li>作為 FileTransmissionGatewayPort 所有遠端操作方法的第一個連線配置參數。</li>
 * </ul>
 * </p>
 *
 * @param host 伺服器主機名稱或 IP 位址，不可為空
 * @param port 連接埠號 (若傳入 <= 0 則採用該協定之預設埠號)
 * @param protocol 傳輸協定 (FTP / FTPS / SFTP)，不可為空
 * @param username 登入使用者名稱，不可為空
 * @param password 登入密碼 (可傳入明文或 ENC(...) 加密密文)
 * @param timeoutMs 連線與傳輸超時時間 (毫秒，預設 10000ms)
 * @param passiveMode 是否啟用被動傳輸模式 (Passive Mode，主要用於 FTP/FTPS)
 * @param strictHostKeyChecking 是否強制檢查嚴格主機金鑰 (用於 SFTP)
 * @param privateKey SSH 私鑰內容 (PEM 格式) 或私鑰檔案路徑 (用於 SFTP，選填)
 * @param passphrase SSH 私鑰解密密碼 (可傳入明文或 ENC(...) 加密密文，選填)
 */
public record TransmissionServerConfig(
        String host,
        int port,
        TransmissionProtocol protocol,
        String username,
        String password,
        int timeoutMs,
        boolean passiveMode,
        boolean strictHostKeyChecking,
        String privateKey,
        String passphrase
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 精簡建構子 (Compact Constructor)：驗證必要連線資訊並套用預設值 (純粹值物件職責)
     */
    public TransmissionServerConfig {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("伺服器主機 host 不能為空");
        }
        if (protocol == null) {
            throw new IllegalArgumentException("傳輸協定 protocol 不能為空");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("使用者帳號 username 不能為空");
        }

        host = host.trim();
        port = (port > 0) ? port : protocol.getDefaultPort();
        username = username.trim();
        password = (password != null) ? password.trim() : "";
        timeoutMs = (timeoutMs > 0) ? timeoutMs : 10000;
        privateKey = (privateKey != null && !privateKey.trim().isEmpty()) ? privateKey.trim() : null;
        passphrase = (passphrase != null && !passphrase.isEmpty()) ? passphrase.trim() : null;
    }

    /**
     * 衍生新實例：使用解密後的密碼與金鑰通行碼建立新的伺服器配置實例 (維持純粹不可變性)
     *
     * @param decryptedPassword 解密後之密碼明文
     * @param decryptedPassphrase 解密後之私鑰通行碼明文
     * @return 包含明文憑證之全新 TransmissionServerConfig 物件實例
     */
    public TransmissionServerConfig withDecryptedCredentials(String decryptedPassword, String decryptedPassphrase) {
        return new TransmissionServerConfig(
                this.host,
                this.port,
                this.protocol,
                this.username,
                (decryptedPassword != null) ? decryptedPassword : "",
                this.timeoutMs,
                this.passiveMode,
                this.strictHostKeyChecking,
                this.privateKey,
                decryptedPassphrase
        );
    }

    /**
     * 向下相容建構子 (8 參數版本)：維持既有程式碼與測試之完全相容性
     */
    public TransmissionServerConfig(
            String host,
            int port,
            TransmissionProtocol protocol,
            String username,
            String password,
            int timeoutMs,
            boolean passiveMode,
            boolean strictHostKeyChecking
    ) {
        this(host, port, protocol, username, password, timeoutMs, passiveMode, strictHostKeyChecking, null, null);
    }

    /**
     * 便捷工廠方法：建立預設連線參數的伺服器配置 (逾時 10 秒、啟用被動模式、停用嚴格主機檢查)
     *
     * @param host 主機名稱或 IP
     * @param port 埠號 (0 代表使用預設)
     * @param protocol 傳輸協定
     * @param username 帳號名稱
     * @param password 密碼
     * @return 伺服器連線配置數值物件
     */
    public static TransmissionServerConfig of(
            String host,
            int port,
            TransmissionProtocol protocol,
            String username,
            String password
    ) {
        return new TransmissionServerConfig(
                host,
                port,
                protocol,
                username,
                password,
                10000,
                true,
                false,
                null,
                null
        );
    }

    /**
     * 便捷工廠方法：建立基於 SSH 私鑰認證的伺服器配置
     *
     * @param host 主機名稱或 IP
     * @param port 埠號 (0 代表使用預設)
     * @param protocol 傳輸協定 (通常為 SFTP)
     * @param username 帳號名稱
     * @param privateKey 私鑰內容或檔案路徑
     * @param passphrase 私鑰解密通行碼 (可為空)
     * @return 伺服器連線配置數值物件
     */
    public static TransmissionServerConfig ofKey(
            String host,
            int port,
            TransmissionProtocol protocol,
            String username,
            String privateKey,
            String passphrase
    ) {
        return new TransmissionServerConfig(
                host,
                port,
                protocol,
                username,
                "",
                10000,
                true,
                false,
                privateKey,
                passphrase
        );
    }

    /**
     * 判定是否配置了 SSH 私鑰
     *
     * @return 若有設置私鑰則回傳 true
     */
    public boolean hasPrivateKey() {
        return privateKey != null && !privateKey.isEmpty();
    }

    /**
     * 判定是否配置了 SSH 私鑰通行碼
     *
     * @return 若有設置通行碼則回傳 true
     */
    public boolean hasPassphrase() {
        return passphrase != null && !passphrase.isEmpty();
    }

    /**
     * 取得主機名稱 (相容方法)
     *
     * @return 主機名稱
     */
    public String getHost() {
        return host;
    }

    /**
     * 取得連接埠號 (相容方法)
     *
     * @return 連接埠號
     */
    public int getPort() {
        return port;
    }

    /**
     * 取得傳輸協定 (相容方法)
     *
     * @return 傳輸協定
     */
    public TransmissionProtocol getProtocol() {
        return protocol;
    }

    /**
     * 取得使用者帳號 (相容方法)
     *
     * @return 使用者名稱
     */
    public String getUsername() {
        return username;
    }

    /**
     * 取得密碼 (相容方法)
     *
     * @return 密碼字串
     */
    public String getPassword() {
        return password;
    }

    /**
     * 取得超時時間毫秒數 (相容方法)
     *
     * @return 超時毫秒
     */
    public int getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * 判斷是否為被動模式 (相容方法)
     *
     * @return 是否為被動模式
     */
    public boolean isPassiveMode() {
        return passiveMode;
    }

    /**
     * 判斷是否強制檢查嚴格主機金鑰 (相容方法)
     *
     * @return 是否強制檢查金鑰
     */
    public boolean isStrictHostKeyChecking() {
        return strictHostKeyChecking;
    }

    /**
     * 取得 SSH 私鑰內容或檔案路徑
     *
     * @return 私鑰字串
     */
    public String getPrivateKey() {
        return privateKey;
    }

    /**
     * 取得 SSH 私鑰解密通行碼
     *
     * @return 通行碼字串
     */
    public String getPassphrase() {
        return passphrase;
    }

    /**
     * 覆寫 toString 實現機密資料日誌脫敏 (Masking)
     * <p>
     * 確保輸出日誌時，絕對不會洩漏明文密碼、Passphrase 或私鑰內容。
     * </p>
     *
     * @return 經遮罩後的配置字串
     */
    @Override
    public String toString() {
        String maskedPassword = (password == null || password.isEmpty()) ? "" : "******";
        String maskedPassphrase = (passphrase == null || passphrase.isEmpty()) ? "" : "******";
        String maskedKey = (privateKey == null || privateKey.isEmpty()) ? "" : "[CONFIGURED]";

        return "TransmissionServerConfig[" +
                "host=" + host +
                ", port=" + port +
                ", protocol=" + protocol +
                ", username=" + username +
                ", password=" + maskedPassword +
                ", timeoutMs=" + timeoutMs +
                ", passiveMode=" + passiveMode +
                ", strictHostKeyChecking=" + strictHostKeyChecking +
                ", privateKey=" + maskedKey +
                ", passphrase=" + maskedPassphrase +
                ']';
    }
}
