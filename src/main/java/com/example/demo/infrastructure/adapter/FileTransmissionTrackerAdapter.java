package com.example.demo.infrastructure.adapter;

import com.example.demo.application.assembler.TransmissionDtoAssembler;
import com.example.demo.application.port.out.FileTransmissionTrackerPort;
import com.example.demo.application.shared.dto.DirectorySyncGottenResult;
import com.example.demo.application.shared.dto.FileTransmissionGottenResult;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.example.demo.infrastructure.persistence.transmission.vo.FileTransmissionId;
import com.example.demo.infrastructure.persistence.transmission.entity.TransmissionSessionEntity;
import com.example.demo.infrastructure.persistence.transmission.repository.TransmissionSessionJpaRepository;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 檔案傳輸追蹤基礎設施適配器 (FileTransmissionTrackerAdapter - Outbound Adapter)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>out : ... + Adapter</code> (AGENTS.md 3-9)。</li>
 *   <li>實作應用層 Outbound Port (FileTransmissionTrackerPort)。</li>
 *   <li>作為傳輸追蹤 (Tracker) 的基礎設施層出口，維護 {@link TransmissionSessionEntity} 實體之狀態流轉 (IN_PROGRESS -> COMPLETED / FAILED)、
 *       時間戳記、耗時計算與審計指標。</li>
 *   <li>協調並呼叫 <code>TransmissionSessionJpaRepository</code> 將作業狀態即時持久化至 RDBMS / H2 資料庫。</li>
 *   <li>徹底將實體操作與持久化細節自 Application Service 剝離，使應用層達成純粹的業務流程編排。</li>
 * </ul>
 * </p>
 */
@Component
public class FileTransmissionTrackerAdapter implements FileTransmissionTrackerPort {

    private final Map<String, TransmissionSessionEntity> activeSessions = new ConcurrentHashMap<>();
    private final TransmissionSessionJpaRepository transmissionSessionJpaRepository;

    /**
     * 建構 FileTransmissionTrackerAdapter 並注入 JPA Repository
     *
     * @param transmissionSessionJpaRepository 傳輸階段 JPA 資料庫存取倉庫
     */
    public FileTransmissionTrackerAdapter(TransmissionSessionJpaRepository transmissionSessionJpaRepository) {
        this.transmissionSessionJpaRepository = Objects.requireNonNull(
                transmissionSessionJpaRepository,
                "transmissionSessionJpaRepository 不能為空"
        );
    }

    @Override
    public String startTracking(TransmissionServerConfig serverConfig) {
        String trackingId = FileTransmissionId.generate().getValue();
        TransmissionSessionEntity session = TransmissionSessionEntity.start(
                trackingId,
                serverConfig.getProtocol().name()
        );
        activeSessions.put(trackingId, session);
        transmissionSessionJpaRepository.save(session);
        return trackingId;
    }

    @Override
    public FileTransmissionGottenResult recordUploadSuccess(
            String trackingId,
            LocalFilePath sourcePath,
            RemoteFilePath targetPath,
            String fileName,
            long fileSizeBytes
    ) {
        TransmissionSessionEntity session = getOrFallback(trackingId);
        session.recordUploadSuccess(
                sourcePath != null ? sourcePath.value() : null,
                targetPath != null ? targetPath.value() : null,
                fileName,
                fileSizeBytes
        );
        activeSessions.remove(trackingId);
        transmissionSessionJpaRepository.save(session);

        return TransmissionDtoAssembler.toUploadResult(
                session.getId(),
                session.getProtocol(),
                session.getStatus().name(),
                sourcePath,
                targetPath,
                fileName,
                fileSizeBytes,
                session.getDurationMillis(),
                session.getErrorMessage()
        );
    }

    @Override
    public FileTransmissionGottenResult recordDownloadSuccess(
            String trackingId,
            RemoteFilePath sourcePath,
            LocalFilePath targetPath,
            String fileName,
            long fileSizeBytes
    ) {
        TransmissionSessionEntity session = getOrFallback(trackingId);
        session.recordDownloadSuccess(
                sourcePath != null ? sourcePath.value() : null,
                targetPath != null ? targetPath.value() : null,
                fileName,
                fileSizeBytes
        );
        activeSessions.remove(trackingId);
        transmissionSessionJpaRepository.save(session);

        return TransmissionDtoAssembler.toDownloadResult(
                session.getId(),
                session.getProtocol(),
                session.getStatus().name(),
                sourcePath,
                targetPath,
                fileName,
                fileSizeBytes,
                session.getDurationMillis(),
                session.getErrorMessage()
        );
    }

    @Override
    public DirectorySyncGottenResult recordSyncSuccess(
            String trackingId,
            SyncDirection direction,
            LocalFilePath localDir,
            RemoteFilePath remoteDir,
            int transferredCount,
            int deletedCount
    ) {
        TransmissionSessionEntity session = getOrFallback(trackingId);
        session.recordSyncSuccess(
                direction != null ? direction.name() : null,
                localDir != null ? localDir.value() : null,
                remoteDir != null ? remoteDir.value() : null,
                transferredCount,
                deletedCount
        );
        activeSessions.remove(trackingId);
        transmissionSessionJpaRepository.save(session);

        return TransmissionDtoAssembler.toSyncResult(
                session.getId(),
                session.getProtocol(),
                session.getStatus().name(),
                direction,
                localDir,
                remoteDir,
                transferredCount,
                deletedCount,
                session.getDurationMillis(),
                session.getErrorMessage()
        );
    }

    @Override
    public void recordFailure(String trackingId, String errorMessage) {
        TransmissionSessionEntity session = activeSessions.remove(trackingId);
        if (session != null) {
            session.recordFailure(errorMessage);
            transmissionSessionJpaRepository.save(session);
        } else if (trackingId != null && !trackingId.trim().isEmpty()) {
            transmissionSessionJpaRepository.findById(trackingId).ifPresent(entity -> {
                entity.recordFailure(errorMessage);
                transmissionSessionJpaRepository.save(entity);
            });
        }
    }

    @Override
    public Optional<FileTransmissionGottenResult> getTransmissionStatus(String trackingId) {
        if (trackingId == null || trackingId.trim().isEmpty()) {
            return Optional.empty();
        }
        TransmissionSessionEntity session = activeSessions.get(trackingId);
        if (session == null) {
            session = transmissionSessionJpaRepository.findById(trackingId).orElse(null);
        }
        if (session == null) {
            return Optional.empty();
        }
        return Optional.of(TransmissionDtoAssembler.toGottenResult(
                session.getId(),
                session.getProtocol(),
                session.getStatus().name(),
                session.getSourcePath(),
                session.getTargetPath(),
                session.getFileName(),
                session.getFileSizeBytes() != null ? session.getFileSizeBytes() : 0L,
                session.getDurationMillis() != null ? session.getDurationMillis() : 0L,
                session.getErrorMessage()
        ));
    }

    /**
     * 獲取進行中實體或安全回退
     *
     * @param trackingId 傳輸追蹤唯一識別碼
     * @return TransmissionSessionJpaEntity 實體
     */
    private TransmissionSessionEntity getOrFallback(String trackingId) {
        if (trackingId != null && !trackingId.trim().isEmpty()) {
            TransmissionSessionEntity existing = activeSessions.get(trackingId);
            if (existing != null) {
                return existing;
            }
            Optional<TransmissionSessionEntity> fromDb = transmissionSessionJpaRepository.findById(trackingId);
            if (fromDb.isPresent()) {
                return fromDb.get();
            }
        }
        String id = (trackingId != null && !trackingId.trim().isEmpty())
                ? trackingId
                : FileTransmissionId.generate().getValue();
        return TransmissionSessionEntity.start(id, "FTP");
    }
}
