package com.example.demo.infrastructure.persistence.credential.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Nationalized;

/**
 * 伺服器傳輸憑證 JPA ORM 實體 (ServerCredentialEntity - ORM Entity)
 * <p>
 * <b>架構與設計職責說明：</b>
 * <ul>
 *   <li><b>分層定位：</b>位於基礎設施持久化層 <code>infrastructure/persistence/credential/entity/</code>。</li>
 *   <li><b>資料庫映射：</b>作為連線憑證在關聯式資料庫 (MSSQL / H2) 中的實體映射，對應資料表 <code>server_credentials</code>。</li>
 *   <li><b>憑證金庫角色 (Credential Vault)：</b>
 *     集中維護本系統連線至各<b>遠端目標傳輸主機 (FTP / FTPS / SFTP Host)</b> 的機器對機器 (M2M) 服務帳號、密碼與 SSH 私鑰。
 *     呼叫端進行傳輸時僅需傳遞 <code>credentialId</code>，無需於 HTTP 請求中反覆暴露遠端主機的連線機密。
 *   </li>
 *   <li><b>憑證歸屬澄清 (Credential Scope)：</b>
 *     本實體所保存之 <code>username</code>、<code>encryptedPassword</code>、<code>encryptedPrivateKey</code> 等，
 *     <b>皆為遠端目標主機的系統帳號與登入憑證</b>，絕對不是本系統或前端的操作者/終端使用者 (End User) 帳密。
 *   </li>
 *   <li><b>落盤加密防護 (At-Rest Encryption)：</b>
 *     所有機密欄位 (密碼、SSH 私鑰、Passphrase) 在寫入資料庫前，一律由 <code>ServerCredentialStorageAdapter</code>
 *     透過內部 AES-GCM 進行對稱加密，以 <code>ENC(...)</code> 密文字串入庫，確保落盤資料之安全性。
 *   </li>
 *   <li><b>充血領域模型 (Rich Domain Model in Entity)：</b>
 *     實體內部提供封裝良好之業務行為方法 (如 {@link #updateDetails}、{@link #updateEncryptedSecrets}、
 *     {@link #activate}、{@link #deactivate})，確保時間戳記與業務狀態一致性。
 *   </li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "server_credentials")
public class ServerCredentialEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 憑證唯一識別碼 (Primary Key，UUID 格式字串)
     */
    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    /**
     * 憑證業務別名 (Unique Business Key，例如 "docker-sftp-server", "tw-bank-sftp")
     * <p>供呼叫端或維運人員直觀識別遠端主機用途，系統內不可重複。</p>
     */
    @Column(name = "alias", length = 64, unique = true, nullable = false)
    private String alias;

    /**
     * 遠端伺服器主機名稱、網域名稱或 IP 位址 (例如 "127.0.0.1", "sftp.example.com")
     * <p>選填欄位；若有配置，傳輸作業可自動引用此主機位址。</p>
     */
    @Column(name = "host", length = 255)
    private String host;

    /**
     * 遠端伺服器通訊埠號 (例如 SFTP 預設 22, FTP 預設 21)
     * <p>選填欄位；若為 null 或 0 則在連線時採用協定之預設埠號。</p>
     */
    @Column(name = "port")
    private Integer port;

    /**
     * 遠端傳輸協定代碼 (SFTP / FTP / FTPS，大寫英文字串)
     * <p>選填欄位；指示該憑證預設配合使用的傳輸通訊協定。</p>
     */
    @Column(name = "protocol", length = 16)
    private String protocol;

    /**
     * 遠端目標伺服器登入帳號 (Remote Host Username / Service Account)
     * <p>
     * <b>重要：</b>此為遠端主機上的登入帳號 (例如 Linux 系統帳號 "tester")，非本應用系統終端使用者帳號。
     * </p>
     */
    @Column(name = "username", length = 128, nullable = false)
    private String username;

    /**
     * 已加密之遠端伺服器登入密碼 (Encrypted Password)
     * <p>
     * <b>安全規範：</b>以 AES-GCM 加密為 <code>ENC(...)</code> 格式密文儲存，資料庫落盤絕不留明文。<br>
     * <b>角色定位：</b>此為遠端主機的登入密碼，非本系統終端使用者密碼，非模組內部加密密鑰。
     * </p>
     */
    @Nationalized
    @Column(name = "encrypted_password", length = 1024)
    private String encryptedPassword;

    /**
     * 已加密之遠端伺服器 SSH 私鑰 (Encrypted SSH Private Key)
     * <p>
     * <b>用途：</b>用於 SFTP 基於非對稱金鑰的身分認證。<br>
     * <b>安全規範：</b>私鑰 PEM 格式內容在入庫前以 AES-GCM 加密為 <code>ENC(...)</code> 格式儲存。
     * </p>
     */
    @Nationalized
    @Column(name = "encrypted_private_key", length = 4096)
    private String encryptedPrivateKey;

    /**
     * 已加密之遠端伺服器 SSH 私鑰解密通行碼 (Encrypted Private Key Passphrase)
     * <p>
     * <b>用途：</b>若 SSH 私鑰本身有設置密碼保護，需提供此 Passphrase 進行解鎖。<br>
     * <b>安全規範：</b>入庫前以 AES-GCM 加密為 <code>ENC(...)</code> 格式儲存。
     * </p>
     */
    @Nationalized
    @Column(name = "encrypted_passphrase", length = 1024)
    private String encryptedPassphrase;

    /**
     * 憑證備註與用途說明 (Description)
     * <p>紀錄該憑證對應的遠端主機用途、環境 (UAT/PROD)、負責聯絡人等補充資訊。</p>
     */
    @Nationalized
    @Column(name = "description", length = 512)
    private String description;

    /**
     * 憑證啟用/有效狀態旗標 (Active Flag)
     * <p>true 表示啟用 (可用於傳輸作業)；false 表示停用 (傳輸時若引用將會被拒絕)。</p>
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /**
     * 憑證建檔建立時間 (UTC Instant)
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * 憑證最後異動更新時間 (UTC Instant)
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * JPA 必要之預設建構子
     */
    protected ServerCredentialEntity() {
    }

    /**
     * 建構子：初始化全新伺服器憑證實體
     *
     * @param id                  唯一識別碼 (若為空則自動生成 UUID)
     * @param alias               憑證別名
     * @param host                主機名稱或 IP
     * @param port                通訊埠
     * @param protocol            傳輸協定 (SFTP / FTP / FTPS)
     * @param username            登入帳號
     * @param encryptedPassword   已加密密碼
     * @param encryptedPrivateKey 已加密私鑰
     * @param encryptedPassphrase 已加密私鑰密碼
     * @param description         備註描述
     */
    public ServerCredentialEntity(String id,
                                  String alias,
                                  String host,
                                  Integer port,
                                  String protocol,
                                  String username,
                                  String encryptedPassword,
                                  String encryptedPrivateKey,
                                  String encryptedPassphrase,
                                  String description) {
        this.id = (id != null && !id.trim().isEmpty()) ? id.trim() : UUID.randomUUID().toString();
        this.alias = Objects.requireNonNull(alias, "alias 不能為空").trim();
        this.host = host != null ? host.trim() : null;
        this.port = port;
        this.protocol = protocol != null ? protocol.trim().toUpperCase() : null;
        this.username = Objects.requireNonNull(username, "username 不能為空").trim();
        this.encryptedPassword = cleanOrNull(encryptedPassword);
        this.encryptedPrivateKey = cleanOrNull(encryptedPrivateKey);
        this.encryptedPassphrase = cleanOrNull(encryptedPassphrase);
        this.description = cleanOrNull(description);
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * 業務方法：更新憑證連線資訊與備註
     */
    public void updateDetails(String alias,
                              String host,
                              Integer port,
                              String protocol,
                              String username,
                              String description) {
        if (alias != null && !alias.trim().isEmpty()) {
            this.alias = alias.trim();
        }
        if (host != null) {
            this.host = cleanOrNull(host);
        }
        if (port != null && port > 0) {
            this.port = port;
        }
        if (protocol != null && !protocol.trim().isEmpty()) {
            this.protocol = protocol.trim().toUpperCase();
        }
        if (username != null && !username.trim().isEmpty()) {
            this.username = username.trim();
        }
        if (description != null) {
            this.description = cleanOrNull(description);
        }
        this.updatedAt = Instant.now();
    }

    /**
     * 業務方法：更新加密之密碼與私鑰憑證資料
     */
    public void updateEncryptedSecrets(String encryptedPassword,
                                      String encryptedPrivateKey,
                                      String encryptedPassphrase) {
        if (encryptedPassword != null) {
            this.encryptedPassword = cleanOrNull(encryptedPassword);
        }
        if (encryptedPrivateKey != null) {
            this.encryptedPrivateKey = cleanOrNull(encryptedPrivateKey);
        }
        if (encryptedPassphrase != null) {
            this.encryptedPassphrase = cleanOrNull(encryptedPassphrase);
        }
        this.updatedAt = Instant.now();
    }

    /**
     * 啟用憑證
     */
    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    /**
     * 停用憑證
     */
    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    private static String cleanOrNull(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        return str.trim();
    }

    // --- Getters & Accessors ---

    /**
     * 取得憑證唯一識別碼
     *
     * @return 憑證 UUID 字串
     */
    public String getId() {
        return id;
    }

    /**
     * 取得憑證業務別名
     *
     * @return 憑證唯一識別別名
     */
    public String getAlias() {
        return alias;
    }

    /**
     * 取得預設遠端主機位址
     *
     * @return 主機名稱、網域名稱或 IP 位址 (可能為 null)
     */
    public String getHost() {
        return host;
    }

    /**
     * 取得預設遠端通訊埠號
     *
     * @return 埠號 (可能為 null)
     */
    public Integer getPort() {
        return port;
    }

    /**
     * 取得預設遠端傳輸協定
     *
     * @return 傳輸協定代碼 (SFTP / FTP / FTPS，可能為 null)
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * 取得遠端目標主機登入帳號
     *
     * @return 遠端主機服務帳號名稱
     */
    public String getUsername() {
        return username;
    }

    /**
     * 取得已加密之遠端主機登入密碼
     *
     * @return AES-GCM 加密後之 ENC(...) 密文字串 (可能為 null)
     */
    public String getEncryptedPassword() {
        return encryptedPassword;
    }

    /**
     * 取得已加密之遠端主機 SSH 私鑰
     *
     * @return AES-GCM 加密後之 ENC(...) 密文字串 (可能為 null)
     */
    public String getEncryptedPrivateKey() {
        return encryptedPrivateKey;
    }

    /**
     * 取得已加密之遠端主機 SSH 私鑰通行碼
     *
     * @return AES-GCM 加密後之 ENC(...) 密文字串 (可能為 null)
     */
    public String getEncryptedPassphrase() {
        return encryptedPassphrase;
    }

    /**
     * 取得憑證備註與用途說明
     *
     * @return 備註說明字串 (可能為 null)
     */
    public String getDescription() {
        return description;
    }

    /**
     * 取得憑證是否為有效啟用狀態
     *
     * @return true 代表啟用，false 代表停用
     */
    public boolean isActive() {
        return active;
    }

    /**
     * 取得憑證建立時間戳記
     *
     * @return 建立時間 (UTC Instant)
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 取得憑證最後更新時間戳記
     *
     * @return 最後更新時間 (UTC Instant)
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
