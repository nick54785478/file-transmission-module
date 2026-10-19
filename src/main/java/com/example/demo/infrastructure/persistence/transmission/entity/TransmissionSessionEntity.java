package com.example.demo.infrastructure.persistence.transmission.entity;

import com.example.demo.application.shared.vo.TransmissionStage;
import com.example.demo.infrastructure.persistence.transmission.vo.TransmissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.Nationalized;

/**
 * 檔案傳輸作業階段 JPA ORM 實體 (TransmissionSessionEntity - ORM Entity)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li><b>分層定位：</b>位於基礎設施持久化層 <code>infrastructure/persistence/transmission/entity/</code>。</li>
 *   <li><b>資料庫映射：</b>對應關聯式資料庫 (MSSQL / H2) 中的 <code>transmission_sessions</code> 資料表。</li>
 *   <li><b>作業歷程追蹤 (Audit & Tracking Vault)：</b>
 *     記錄每一次檔案傳輸任務 (檔案上傳、檔案下載、目錄同步、非同步作業) 的端到端生命週期，
 *     包含協定、狀態、來源路徑、目標路徑、傳輸位元組、執行耗時以及例外失敗原因。
 *   </li>
 *   <li><b>多國語系支援 (Nationalized UTF-8)：</b>
 *     字串屬性 (來源路徑、目標路徑、檔名、錯誤訊息) 標註 <code>@Nationalized</code> 映射至資料庫 <code>NVARCHAR</code> 欄位，
 *     確保跨平台中文檔名、特殊路徑與例外堆疊無亂碼產生。</li>
 *   <li><b>充血領域模型 (Rich Domain Entity)：</b>
 *     封裝傳輸狀態機流轉邏輯 (如 {@link #start}、{@link #recordUploadSuccess}、
 *     {@link #recordDownloadSuccess}、{@link #recordSyncSuccess}、{@link #recordFailure})
 *     以及動態耗時計算 {@link #calculateDurationMillis}，避免貧血模型與邏輯外溢。</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "transmission_sessions")
public class TransmissionSessionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 傳輸作業唯一識別碼 (Primary Key，UUID 格式字串)
     */
    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    /**
     * 樂觀鎖版本控制欄位 (Optimistic Locking Version)
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * 傳輸通訊協定代碼 (FTP / FTPS / SFTP，大寫英文字串)
     */
    @Column(name = "protocol", length = 16, nullable = false)
    private String protocol;

    /**
     * 傳輸作業當前狀態 (TransmissionStatus: PENDING / IN_PROGRESS / COMPLETED / FAILED)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private TransmissionStatus status;

    /**
     * 內部細部作業階段 (STAGING, VERIFYING, COMMITTING, COMPLETED, FAILED)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 32)
    private TransmissionStage stage;

    /**
     * 來源端檔案或目錄完整路徑 (Source Path)
     * <p>上傳作業為本地來源路徑；下載作業為遠端來源路徑；目錄同步作業為本機目錄路徑。</p>
     */
    @Nationalized
    @Column(name = "source_path", length = 1024)
    private String sourcePath;

    /**
     * 目標端檔案或目錄完整路徑 (Target Path)
     * <p>上傳作業為遠端目標路徑；下載作業為本機目標路徑；目錄同步作業為遠端目錄路徑。</p>
     */
    @Nationalized
    @Column(name = "target_path", length = 1024)
    private String targetPath;

    /**
     * 遠端暫存檔案完整路徑 (用於 2PC 階段追蹤與超時補償清理)
     */
    @Nationalized
    @Column(name = "staging_path", length = 1024)
    private String stagingPath;

    /**
     * 傳輸檔案名稱或作業標記 (File Name / Direction Marker)
     * <p>單檔傳輸時為實際檔案名稱；目錄同步作業時為同步方向 (例如 LOCAL_TO_REMOTE)。</p>
     */
    @Nationalized
    @Column(name = "file_name", length = 512)
    private String fileName;

    /**
     * 傳輸資料大小位元組數 (File Size in Bytes)
     * <p>單檔傳輸時代表傳輸總 Bytes；目錄同步時代表成功同步之檔案數量。</p>
     */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /**
     * 傳輸整體耗時時間 (Duration in Milliseconds)
     * <p>由 completedAt 與 startedAt 計算得出之總毫秒數。</p>
     */
    @Column(name = "duration_millis")
    private Long durationMillis;

    /**
     * 失敗例外原因與錯誤訊息 (Error Message)
     * <p>若傳輸作業失敗，記錄遠端逾時、身分認證失敗、IO 異常等詳細錯誤訊息或例外堆疊摘錄。</p>
     */
    @Nationalized
    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    /**
     * 傳輸作業發起/開始時間戳記 (UTC Instant)
     */
    @Column(name = "started_at")
    private Instant startedAt;

    /**
     * 傳輸作業完成或失敗結束時間戳記 (UTC Instant)
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * 是否經過背景對帳引擎自癒或終態確認標記
     */
    @Column(name = "reconciled", nullable = false)
    private Boolean reconciled = false;

    /**
     * 遠端主機 IP 或域名 (供對帳引擎探測與重試連線)
     */
    @Nationalized
    @Column(name = "server_host", length = 256)
    private String serverHost;

    /**
     * 遠端主機通訊埠
     */
    @Column(name = "server_port")
    private Integer serverPort;

    /**
     * 遠端主機服務帳號
     */
    @Nationalized
    @Column(name = "server_username", length = 128)
    private String serverUsername;

    /**
     * 遠端主機密碼 (加密或連線憑證)
     */
    @Nationalized
    @Column(name = "server_password", length = 512)
    private String serverPassword;

    /**
     * 伺服器憑證 ID (若由憑證管理模組解析)
     */
    @Nationalized
    @Column(name = "credential_id", length = 64)
    private String credentialId;

    /**
     * JPA 規範所需之預設無參建構子
     */
    protected TransmissionSessionEntity() {
    }

    /**
     * 受保護之全參數建構子：禁止外部直接任意 new，一律透過靜態工廠方法建立以維護實體狀態一致性
     *
     * @param id             傳輸作業識別碼 (主鍵 UUID)
     * @param protocol       傳輸通訊協定 (FTP, FTPS, SFTP)
     * @param status         傳輸狀態 (PENDING, IN_PROGRESS, COMPLETED, FAILED)
     * @param sourcePath     來源端檔案或目錄路徑
     * @param targetPath     目標端檔案或目錄路徑
     * @param fileName       檔案名稱或作業方向標記
     * @param fileSizeBytes  傳輸資料位元組大小或同步筆數
     * @param durationMillis 傳輸整體耗時 (毫秒)
     * @param errorMessage   錯誤原因與例外訊息 (若成功則為 null)
     * @param startedAt      作業開始時間
     * @param completedAt    作業結束時間
     */
    protected TransmissionSessionEntity(
            String id,
            String protocol,
            TransmissionStatus status,
            String sourcePath,
            String targetPath,
            String fileName,
            Long fileSizeBytes,
            Long durationMillis,
            String errorMessage,
            Instant startedAt,
            Instant completedAt
    ) {
        this.id = Objects.requireNonNull(id, "id 不能為空");
        this.protocol = protocol;
        this.status = status;
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.fileName = fileName;
        this.fileSizeBytes = fileSizeBytes;
        this.durationMillis = durationMillis;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    /**
     * 【業務工廠方法】啟動新傳輸工作階段 (標準業務生命週期起點)
     * <p>狀態初始化為 IN_PROGRESS，階段設為 STAGING，並自動記錄 startedAt 時間戳記。</p>
     *
     * @param id 傳輸唯一識別碼 (UUID)
     * @param protocol 傳輸協定名稱 (FTP, FTPS, SFTP)
     * @return 初始中的傳輸階段實體
     */
    public static TransmissionSessionEntity start(String id, String protocol) {
        TransmissionSessionEntity entity = new TransmissionSessionEntity();
        entity.id = Objects.requireNonNull(id, "id 不能為空");
        entity.protocol = Objects.requireNonNull(protocol, "protocol 不能為空");
        entity.status = TransmissionStatus.IN_PROGRESS;
        entity.stage = TransmissionStage.STAGING;
        entity.startedAt = Instant.now();
        entity.version = null;
        entity.reconciled = false;
        return entity;
    }

    /**
     * 【業務工廠方法】啟動新傳輸工作階段 (含遠端伺服器連線與憑證識別資訊，供對帳與自癒探測使用)
     *
     * @param id 傳輸唯一識別碼 (UUID)
     * @param protocol 傳輸協定名稱 (FTP, FTPS, SFTP)
     * @param serverHost 遠端主機 IP 或域名
     * @param serverPort 遠端主機埠號
     * @param serverUsername 遠端登入服務帳號
     * @param credentialId 關聯之伺服器憑證 ID (選填)
     * @return 初始中的傳輸階段實體
     */
    public static TransmissionSessionEntity start(
            String id,
            String protocol,
            String serverHost,
            Integer serverPort,
            String serverUsername,
            String serverPassword,
            String credentialId
    ) {
        TransmissionSessionEntity entity = start(id, protocol);
        entity.serverHost = serverHost;
        entity.serverPort = serverPort;
        entity.serverUsername = serverUsername;
        entity.serverPassword = serverPassword;
        entity.credentialId = credentialId;
        return entity;
    }

    /**
     * 【重建工廠方法】用於從歷史資料、備份或測試情境完整重建實體 (Reconstitution)
     *
     * @param id             傳輸作業識別碼 (主鍵 UUID)
     * @param protocol       傳輸通訊協定 (FTP, FTPS, SFTP)
     * @param status         傳輸狀態 (PENDING, IN_PROGRESS, COMPLETED, FAILED)
     * @param sourcePath     來源端檔案或目錄路徑
     * @param targetPath     目標端檔案或目錄路徑
     * @param fileName       檔案名稱或作業方向標記
     * @param fileSizeBytes  傳輸資料位元組大小或同步筆數
     * @param durationMillis 傳輸整體耗時 (毫秒)
     * @param errorMessage   錯誤原因與例外訊息 (若成功則為 null)
     * @param startedAt      作業開始時間
     * @param completedAt    作業結束時間
     * @return 完整重建之傳輸階段實體
     */
    public static TransmissionSessionEntity reconstitute(
            String id,
            String protocol,
            TransmissionStatus status,
            String sourcePath,
            String targetPath,
            String fileName,
            Long fileSizeBytes,
            Long durationMillis,
            String errorMessage,
            Instant startedAt,
            Instant completedAt
    ) {
        TransmissionSessionEntity entity = new TransmissionSessionEntity(
                id,
                protocol,
                status,
                sourcePath,
                targetPath,
                fileName,
                fileSizeBytes,
                durationMillis,
                errorMessage,
                startedAt,
                completedAt
        );
        return entity;
    }

    /**
     * 記錄進入 2PC 遠端暫存寫入階段 (STAGING)
     *
     * @param stagingPath 遠端暫存檔案完整路徑
     */
    public void recordStaging(String stagingPath) {
        this.stage = TransmissionStage.STAGING;
        this.stagingPath = stagingPath;
    }

    /**
     * 記錄進入完整性與大小檢核階段 (VERIFYING)
     */
    public void recordVerifying() {
        this.stage = TransmissionStage.VERIFYING;
    }

    /**
     * 記錄進入原子更名提交階段 (COMMITTING)
     */
    public void recordCommitting() {
        this.stage = TransmissionStage.COMMITTING;
    }

    /**
     * 記錄背景對帳引擎自動自癒成功 (Reconciled Success)
     *
     * @param fileSizeBytes 遠端探測確認之檔案大小
     */
    public void recordReconciledSuccess(long fileSizeBytes) {
        this.status = TransmissionStatus.COMPLETED;
        this.stage = TransmissionStage.COMPLETED;
        this.reconciled = true;
        this.fileSizeBytes = fileSizeBytes;
        this.completedAt = Instant.now();
        this.durationMillis = calculateDurationMillis();
        this.errorMessage = (this.errorMessage != null)
                ? this.errorMessage + " [RECONCILED_SUCCESS]"
                : "[RECONCILED_SUCCESS]";
    }

    /**
     * 記錄背景對帳引擎自癒失敗或逾時清除清理 (Reconciled Failure)
     *
     * @param failureReason 判定失敗之原因註記
     */
    public void recordReconciledFailure(String failureReason) {
        this.status = TransmissionStatus.FAILED;
        this.stage = TransmissionStage.FAILED;
        this.reconciled = true;
        this.errorMessage = failureReason;
        this.completedAt = Instant.now();
        this.durationMillis = calculateDurationMillis();
    }

    /**
     * 記錄檔案上傳成功狀態與中繼資訊
     *
     * @param sourcePath 本機來源檔案路徑
     * @param targetPath 遠端目標目錄路徑
     * @param fileName 檔案名稱
     * @param fileSizeBytes 檔案位元組大小
     */
    public void recordUploadSuccess(String sourcePath, String targetPath, String fileName, long fileSizeBytes) {
        this.status = TransmissionStatus.COMPLETED;
        this.stage = TransmissionStage.COMPLETED;
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.fileName = fileName;
        this.fileSizeBytes = fileSizeBytes;
        this.completedAt = Instant.now();
        this.durationMillis = calculateDurationMillis();
    }

    /**
     * 記錄檔案下載成功狀態與中繼資訊
     *
     * @param sourcePath 遠端來源檔案路徑
     * @param targetPath 本機目標檔案路徑
     * @param fileName 檔案名稱
     * @param fileSizeBytes 檔案位元組大小
     */
    public void recordDownloadSuccess(String sourcePath, String targetPath, String fileName, long fileSizeBytes) {
        this.status = TransmissionStatus.COMPLETED;
        this.stage = TransmissionStage.COMPLETED;
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.fileName = fileName;
        this.fileSizeBytes = fileSizeBytes;
        this.completedAt = Instant.now();
        this.durationMillis = calculateDurationMillis();
    }

    /**
     * 記錄目錄同步成功狀態與統計資訊
     *
     * @param direction 同步方向
     * @param localDir 本機目錄路徑
     * @param remoteDir 遠端目錄路徑
     * @param transferredCount 成功傳輸檔案數
     * @param deletedCount 刪除多餘檔案數
     */
    public void recordSyncSuccess(String direction, String localDir, String remoteDir, int transferredCount, int deletedCount) {
        this.status = TransmissionStatus.COMPLETED;
        this.stage = TransmissionStage.COMPLETED;
        this.sourcePath = localDir;
        this.targetPath = remoteDir;
        this.fileName = direction;
        this.fileSizeBytes = (long) transferredCount;
        this.completedAt = Instant.now();
        this.durationMillis = calculateDurationMillis();
    }

    /**
     * 記錄作業失敗狀態與錯誤訊息
     *
     * @param errorMessage 錯誤訊息
     */
    public void recordFailure(String errorMessage) {
        this.status = TransmissionStatus.FAILED;
        this.stage = TransmissionStage.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
        this.durationMillis = calculateDurationMillis();
    }

    /**
     * 計算傳輸耗時 (毫秒)
     *
     * @return 耗時毫秒數
     */
    public long calculateDurationMillis() {
        if (startedAt == null) {
            return 0L;
        }
        Instant end = (completedAt != null) ? completedAt : Instant.now();
        return Duration.between(startedAt, end).toMillis();
    }

    // --- Getters & Setters ---

    /**
     * 取得傳輸作業唯一識別碼
     *
     * @return 傳輸識別碼 UUID
     */
    public String getId() {
        return id;
    }

    /**
     * 設定傳輸作業唯一識別碼
     *
     * @param id 傳輸識別碼 UUID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 取得傳輸通訊協定代碼
     *
     * @return 傳輸協定 (FTP / FTPS / SFTP)
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * 設定傳輸通訊協定代碼
     *
     * @param protocol 傳輸協定 (FTP / FTPS / SFTP)
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * 取得傳輸作業當前狀態
     *
     * @return 傳輸狀態列舉值
     */
    public TransmissionStatus getStatus() {
        return status;
    }

    /**
     * 設定傳輸作業當前狀態
     *
     * @param status 傳輸狀態列舉值
     */
    public void setStatus(TransmissionStatus status) {
        this.status = status;
    }

    /**
     * 取得傳輸來源路徑
     *
     * @return 本地或遠端來源檔案/目錄路徑
     */
    public String getSourcePath() {
        return sourcePath;
    }

    /**
     * 設定傳輸來源路徑
     *
     * @param sourcePath 本地或遠端來源檔案/目錄路徑
     */
    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    /**
     * 取得傳輸目的路徑
     *
     * @return 遠端或本地目標檔案/目錄路徑
     */
    public String getTargetPath() {
        return targetPath;
    }

    /**
     * 設定傳輸目的路徑
     *
     * @param targetPath 遠端或本地目標檔案/目錄路徑
     */
    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    /**
     * 取得傳輸檔案名稱或作業方向標記
     *
     * @return 檔案名稱或同步方向
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 設定傳輸檔案名稱或作業方向標記
     *
     * @param fileName 檔案名稱或同步方向
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * 取得傳輸檔案大小位元組數或同步筆數
     *
     * @return 位元組數大小或同步筆數
     */
    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    /**
     * 設定傳輸檔案大小位元組數或同步筆數
     *
     * @param fileSizeBytes 位元組數大小或同步筆數
     */
    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    /**
     * 取得傳輸總耗時 (毫秒)
     *
     * @return 耗時毫秒數
     */
    public Long getDurationMillis() {
        return durationMillis;
    }

    /**
     * 設定傳輸總耗時 (毫秒)
     *
     * @param durationMillis 耗時毫秒數
     */
    public void setDurationMillis(Long durationMillis) {
        this.durationMillis = durationMillis;
    }

    /**
     * 取得傳輸失敗錯誤訊息
     *
     * @return 錯誤訊息或例外原因 (若成功則為 null)
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 設定傳輸失敗錯誤訊息
     *
     * @param errorMessage 錯誤訊息或例外原因
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * 取得傳輸開始時間戳記
     *
     * @return 開始時間 (UTC Instant)
     */
    public Instant getStartedAt() {
        return startedAt;
    }

    /**
     * 設定傳輸開始時間戳記
     *
     * @param startedAt 開始時間 (UTC Instant)
     */
    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    /**
     * 取得傳輸完成或失敗結束時間戳記
     *
     * @return 結束時間 (UTC Instant)
     */
    public Instant getCompletedAt() {
        return completedAt;
    }

    /**
     * 設定傳輸完成或失敗結束時間戳記
     *
     * @param completedAt 結束時間 (UTC Instant)
     */
    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    /**
     * 取得樂觀鎖版本號
     *
     * @return 版本號
     */
    public Long getVersion() {
        return version != null ? version : 0L;
    }

    /**
     * 設定樂觀鎖版本號
     *
     * @param version 版本號
     */
    public void setVersion(Long version) {
        this.version = version;
    }

    /**
     * 取得內部作業階段
     *
     * @return TransmissionStage 列舉
     */
    public TransmissionStage getStage() {
        return stage;
    }

    /**
     * 設定內部作業階段
     *
     * @param stage TransmissionStage 列舉
     */
    public void setStage(TransmissionStage stage) {
        this.stage = stage;
    }

    /**
     * 取得遠端暫存檔案完整路徑
     *
     * @return 暫存路徑字串
     */
    public String getStagingPath() {
        return stagingPath;
    }

    /**
     * 設定遠端暫存檔案完整路徑
     *
     * @param stagingPath 暫存路徑字串
     */
    public void setStagingPath(String stagingPath) {
        this.stagingPath = stagingPath;
    }

    /**
     * 取得是否經由對帳自癒處理
     *
     * @return 是否自癒
     */
    public Boolean getReconciled() {
        return reconciled;
    }

    /**
     * 設定是否經由對帳自癒處理
     *
     * @param reconciled 是否自癒
     */
    public void setReconciled(Boolean reconciled) {
        this.reconciled = reconciled;
    }

    /**
     * 取得遠端主機 IP 或域名
     *
     * @return 遠端主機位址
     */
    public String getServerHost() {
        return serverHost;
    }

    /**
     * 設定遠端主機 IP 或域名
     *
     * @param serverHost 遠端主機位址
     */
    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    /**
     * 取得遠端主機通訊埠
     *
     * @return 遠端通訊埠號
     */
    public Integer getServerPort() {
        return serverPort;
    }

    /**
     * 設定遠端主機通訊埠
     *
     * @param serverPort 遠端通訊埠號
     */
    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    /**
     * 取得遠端主機服務帳號
     *
     * @return 服務帳號字串
     */
    public String getServerUsername() {
        return serverUsername;
    }

    /**
     * 設定遠端主機服務帳號
     *
     * @param serverUsername 服務帳號字串
     */
    public void setServerUsername(String serverUsername) {
        this.serverUsername = serverUsername;
    }

    /**
     * 取得關聯之伺服器憑證 ID
     *
     * @return 憑證 ID
     */
    public String getCredentialId() {
        return credentialId;
    }

    /**
     * 設定關聯之伺服器憑證 ID
     *
     * @param credentialId 憑證 ID
     */
    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    /**
     * 取得遠端伺服器密碼
     *
     * @return 伺服器密碼
     */
    public String getServerPassword() {
        return serverPassword;
    }

    /**
     * 設定遠端伺服器密碼
     *
     * @param serverPassword 伺服器密碼
     */
    public void setServerPassword(String serverPassword) {
        this.serverPassword = serverPassword;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TransmissionSessionEntity that = (TransmissionSessionEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TransmissionSessionEntity{" +
                "id='" + id + '\'' +
                ", protocol='" + protocol + '\'' +
                ", status=" + status +
                ", fileName='" + fileName + '\'' +
                ", durationMillis=" + durationMillis +
                '}';
    }
}
