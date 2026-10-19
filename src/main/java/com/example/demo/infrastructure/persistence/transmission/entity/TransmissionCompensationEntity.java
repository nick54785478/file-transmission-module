package com.example.demo.infrastructure.persistence.transmission.entity;

import com.example.demo.application.shared.vo.CompensationAction;
import com.example.demo.application.shared.vo.CompensationStatus;
import com.example.demo.application.shared.vo.CompensationTask;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.Nationalized;

/**
 * Saga 補償交易日誌 JPA ORM 實體 (TransmissionCompensationEntity - ORM Entity)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li><b>分層定位：</b>位於基礎設施持久化層 <code>infrastructure/persistence/transmission/entity/</code>。</li>
 *   <li><b>資料庫映射：</b>對應關聯式資料庫中的 <code>transmission_compensation_log</code> 資料表。</li>
 *   <li><b>Saga 本地 Outbox 模式：</b>當遠端傳輸失敗且即時補償刪除失敗時，記錄補償任務、遠端目標路徑與伺服器連線配置，供背景補償排程指數退避重試。</li>
 * </ul>
 * </p>
 */
@Entity
@Table(
        name = "transmission_compensation_log",
        indexes = {
                @Index(name = "idx_comp_pending", columnList = "status, next_retry_at")
        }
)
public class TransmissionCompensationEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 補償任務主鍵識別碼 (自動遞增)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "compensation_id")
    private Long id;

    /**
     * 關聯之傳輸作業追蹤唯一識別碼
     */
    @Nationalized
    @Column(name = "tracking_id", length = 64, nullable = false)
    private String trackingId;

    /**
     * 補償行為類型 (DELETE_REMOTE_STAGING, RENAME_BACK, DELETE_LOCAL_ORPHAN)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 32, nullable = false)
    private CompensationAction actionType;

    /**
     * 傳輸通訊協定代碼 (FTP / FTPS / SFTP)
     */
    @Column(name = "protocol", length = 16, nullable = false)
    private String protocol;

    /**
     * 遠端主機 IP 或網域名稱
     */
    @Nationalized
    @Column(name = "server_host", length = 256, nullable = false)
    private String serverHost;

    /**
     * 遠端通訊埠號
     */
    @Column(name = "server_port", nullable = false)
    private int serverPort;

    /**
     * 遠端登入服務帳號
     */
    @Nationalized
    @Column(name = "server_username", length = 128, nullable = false)
    private String serverUsername;

    /**
     * 遠端伺服器認證密碼
     */
    @Nationalized
    @Column(name = "server_password", length = 512)
    private String serverPassword;

    /**
     * 伺服器憑證 ID (選填)
     */
    @Nationalized
    @Column(name = "credential_id", length = 64)
    private String credentialId;

    /**
     * 欲補償處理之遠端路徑 (例如殘留的 .staging 暫存檔)
     */
    @Nationalized
    @Column(name = "remote_path", length = 1024, nullable = false)
    private String remotePath;

    /**
     * 當前已嘗試重試次數
     */
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    /**
     * 最大重試次數上限
     */
    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 5;

    /**
     * 當前補償任務狀態 (PENDING / RETRYING / SUCCESS / ABANDONED)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private CompensationStatus status;

    /**
     * 最近一次執行失敗錯誤原因
     */
    @Nationalized
    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    /**
     * 下一次預定重試時間戳記 (UTC Instant)
     */
    @Column(name = "next_retry_at", nullable = false)
    private Instant nextRetryAt;

    /**
     * 建立時間戳記 (UTC Instant)
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * 最近更新時間戳記 (UTC Instant)
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * JPA 規範所需之預設無參建構子
     */
    protected TransmissionCompensationEntity() {
    }

    /**
     * 建立初始 PENDING 狀態之補償實體工廠方法
     *
     * @param task 領域補償任務物件
     * @return 初始之補償實體
     */
    public static TransmissionCompensationEntity fromDomain(CompensationTask task) {
        TransmissionCompensationEntity entity = new TransmissionCompensationEntity();
        entity.trackingId = task.trackingId();
        entity.actionType = task.actionType();
        TransmissionServerConfig cfg = task.serverConfig();
        entity.protocol = cfg.protocol().name();
        entity.serverHost = cfg.host();
        entity.serverPort = cfg.port();
        entity.serverUsername = cfg.username();
        entity.serverPassword = cfg.password();
        entity.remotePath = task.remotePath();
        entity.retryCount = task.retryCount();
        entity.maxRetries = task.maxRetries();
        entity.status = task.status();
        entity.errorMessage = task.errorMessage();
        entity.nextRetryAt = task.nextRetryAt();
        Instant now = Instant.now();
        entity.createdAt = (task.createdAt() != null) ? task.createdAt() : now;
        entity.updatedAt = (task.updatedAt() != null) ? task.updatedAt() : now;
        return entity;
    }

    /**
     * 轉換為領域 CompensationTask 數值物件
     *
     * @return CompensationTask 數值物件
     */
    public CompensationTask toDomain() {
        TransmissionServerConfig serverConfig = new TransmissionServerConfig(
                this.serverHost,
                this.serverPort,
                TransmissionProtocol.valueOf(this.protocol),
                this.serverUsername,
                this.serverPassword,
                10000,
                true,
                false,
                null,
                null
        );

        return new CompensationTask(
                this.id,
                this.trackingId,
                this.actionType,
                serverConfig,
                this.remotePath,
                this.retryCount,
                this.maxRetries,
                this.status,
                this.errorMessage,
                this.nextRetryAt,
                this.createdAt,
                this.updatedAt
        );
    }

    /**
     * 記錄補償執行成功
     */
    public void markSuccess() {
        this.status = CompensationStatus.SUCCESS;
        this.updatedAt = Instant.now();
    }

    /**
     * 記錄重試失敗並依退避延遲更新下次重試時間
     *
     * @param error 失敗錯誤訊息
     * @param backoffDelay 下次重試延遲時間
     */
    public void recordRetryFailure(String error, Duration backoffDelay) {
        this.retryCount++;
        this.errorMessage = error;
        this.updatedAt = Instant.now();
        if (this.retryCount >= this.maxRetries) {
            this.status = CompensationStatus.ABANDONED;
        } else {
            this.status = CompensationStatus.RETRYING;
            this.nextRetryAt = Instant.now().plus(backoffDelay);
        }
    }

    // --- Getters & Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTrackingId() {
        return trackingId;
    }

    public void setTrackingId(String trackingId) {
        this.trackingId = trackingId;
    }

    public CompensationAction getActionType() {
        return actionType;
    }

    public void setActionType(CompensationAction actionType) {
        this.actionType = actionType;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getServerUsername() {
        return serverUsername;
    }

    public void setServerUsername(String serverUsername) {
        this.serverUsername = serverUsername;
    }

    public String getServerPassword() {
        return serverPassword;
    }

    public void setServerPassword(String serverPassword) {
        this.serverPassword = serverPassword;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getRemotePath() {
        return remotePath;
    }

    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public CompensationStatus getStatus() {
        return status;
    }

    public void setStatus(CompensationStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
