package com.example.demo.infrastructure.persistence.transmission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.Nationalized;

/**
 * 目錄同步單檔項目檢查點 JPA ORM 實體 (TransmissionSessionItemEntity - ORM Entity)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li><b>分層定位：</b>位於基礎設施持久化層 <code>infrastructure/persistence/transmission/entity/</code>。</li>
 *   <li><b>資料庫映射：</b>對應關聯式資料庫中的 <code>transmission_session_items</code> 資料表。</li>
 *   <li><b>斷點續傳檢查點 (Checkpoints)：</b>
 *     在目錄同步作業中記錄每一個子檔案項目的傳輸成果，避免大批量檔案同步中斷後重頭重傳。</li>
 * </ul>
 * </p>
 */
@Entity
@Table(
        name = "transmission_session_items",
        indexes = {
                @Index(name = "idx_item_session", columnList = "session_id")
        }
)
public class TransmissionSessionItemEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long id;

    @Nationalized
    @Column(name = "session_id", length = 64, nullable = false)
    private String sessionId;

    @Nationalized
    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Nationalized
    @Column(name = "relative_path", length = 1000, nullable = false)
    private String relativePath;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Nationalized
    @Column(name = "error_message", length = 2048)
    private String errorMessage;

    @Column(name = "transferred_at", nullable = false)
    private Instant transferredAt;

    protected TransmissionSessionItemEntity() {
    }

    public static TransmissionSessionItemEntity of(
            String sessionId,
            String fileName,
            String relativePath,
            long fileSizeBytes,
            String status,
            String errorMessage
    ) {
        TransmissionSessionItemEntity entity = new TransmissionSessionItemEntity();
        entity.sessionId = Objects.requireNonNull(sessionId, "sessionId 不能為空");
        entity.fileName = Objects.requireNonNull(fileName, "fileName 不能為空");
        entity.relativePath = Objects.requireNonNull(relativePath, "relativePath 不能為空");
        entity.fileSizeBytes = fileSizeBytes;
        entity.status = Objects.requireNonNull(status, "status 不能為空");
        entity.errorMessage = errorMessage;
        entity.transferredAt = Instant.now();
        return entity;
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getTransferredAt() {
        return transferredAt;
    }
}
