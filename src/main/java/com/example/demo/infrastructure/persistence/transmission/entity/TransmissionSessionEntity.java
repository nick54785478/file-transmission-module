package com.example.demo.infrastructure.persistence.transmission.entity;

import com.example.demo.infrastructure.persistence.transmission.vo.TransmissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
     * <p>狀態初始化為 IN_PROGRESS，並自動記錄 startedAt 時間戳記。</p>
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
        entity.startedAt = Instant.now();
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
        return new TransmissionSessionEntity(
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
