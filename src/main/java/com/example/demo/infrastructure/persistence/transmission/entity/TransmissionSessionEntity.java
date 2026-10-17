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
 * 檔案傳輸作業階段 JPA ORM 實體 (TransmissionSessionJpaEntity - ORM Entity)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>... + JpaEntity</code> (AGENTS.md 第 2 節)。</li>
 *   <li>位於 <code>infrastructure/persistence/transmission/entity/</code>。</li>
 *   <li>作為傳輸作業階段在關聯式資料庫 (H2 / RDBMS / MSSQL) 中的實體映射，對應表 <code>transmission_sessions</code>。</li>
 *   <li>字串屬性標註 <code>@Nationalized</code> 映射至 MSSQL <code>NVARCHAR</code> 欄位，確保多國語系路徑與錯誤訊息無亂碼。</li>
 *   <li><b>充血模型 (Rich Entity)：</b> 封裝單次傳輸階段的完整生命週期與狀態流轉 (IN_PROGRESS -> COMPLETED / FAILED)、
 *       耗時計算、成功/失敗中繼資料寫入與時間戳記維護，避免貧血模型。</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "transmission_sessions")
public class TransmissionSessionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "protocol", length = 16, nullable = false)
    private String protocol;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private TransmissionStatus status;

    @Nationalized
    @Column(name = "source_path", length = 1024)
    private String sourcePath;

    @Nationalized
    @Column(name = "target_path", length = 1024)
    private String targetPath;

    @Nationalized
    @Column(name = "file_name", length = 512)
    private String fileName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "duration_millis")
    private Long durationMillis;

    @Nationalized
    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * JPA 規範所需之預設無參建構子
     */
    protected TransmissionSessionEntity() {
    }

    /**
     * 建構完整的 JPA 傳輸紀錄實體
     *
     * @param id 傳輸識別碼 (主鍵)
     * @param protocol 通訊協定 (FTP, FTPS, SFTP)
     * @param status 傳輸狀態
     * @param sourcePath 來源路徑
     * @param targetPath 目標路徑
     * @param fileName 檔案名稱
     * @param fileSizeBytes 傳輸檔案位元組
     * @param durationMillis 傳輸耗時 (毫秒)
     * @param errorMessage 錯誤訊息
     * @param startedAt 開始時間
     * @param completedAt 完成時間
     */
    public TransmissionSessionEntity(
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
     * 靜態工廠方法：建立初始傳輸階段實體並自動標記為進行中狀態
     *
     * @param id 傳輸唯一識別碼
     * @param protocol 傳輸協定名稱 (FTP, FTPS, SFTP)
     * @return 初始化的傳輸階段實體
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public TransmissionStatus getStatus() {
        return status;
    }

    public void setStatus(TransmissionStatus status) {
        this.status = status;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(Long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

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
        return "TransmissionSessionJpaEntity{" +
                "id='" + id + '\'' +
                ", protocol='" + protocol + '\'' +
                ", status=" + status +
                ", fileName='" + fileName + '\'' +
                ", durationMillis=" + durationMillis +
                '}';
    }
}
