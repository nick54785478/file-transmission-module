package com.example.demo.infrastructure.adapter;

import com.example.demo.application.assembler.TransmissionDtoAssembler;
import com.example.demo.application.port.out.FileTransmissionTrackerPort;
import com.example.demo.application.shared.dto.DirectorySyncGottenResult;
import com.example.demo.application.shared.dto.FileDownloadGottenResult;
import com.example.demo.application.shared.dto.FileUploadGottenResult;
import com.example.demo.application.shared.dto.TransmissionStatusGottenResult;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.example.demo.infrastructure.persistence.transmission.vo.FileTransmissionId;
import com.example.demo.infrastructure.persistence.transmission.entity.TransmissionSessionEntity;
import com.example.demo.infrastructure.persistence.transmission.repository.TransmissionSessionJpaRepository;
import com.example.demo.application.shared.dto.ZombieSessionGottenResult;
import com.example.demo.infrastructure.persistence.transmission.vo.TransmissionStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.OptimisticLockingFailureException;
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
                serverConfig.getProtocol().name(),
                serverConfig.host(),
                serverConfig.port(),
                serverConfig.username(),
                serverConfig.password(),
                null
        );
        TransmissionSessionEntity saved = transmissionSessionJpaRepository.save(session);
        activeSessions.put(trackingId, saved != null ? saved : session);
        return trackingId;
    }

    @Override
    public void recordStaging(String trackingId, String stagingPath) {
        TransmissionSessionEntity session = getOrFallback(trackingId);
        session.recordStaging(stagingPath);
        TransmissionSessionEntity saved = transmissionSessionJpaRepository.save(session);
        activeSessions.put(trackingId, saved != null ? saved : session);
    }

    @Override
    public void recordVerifying(String trackingId) {
        TransmissionSessionEntity session = getOrFallback(trackingId);
        session.recordVerifying();
        TransmissionSessionEntity saved = transmissionSessionJpaRepository.save(session);
        activeSessions.put(trackingId, saved != null ? saved : session);
    }

    @Override
    public void recordCommitting(String trackingId) {
        TransmissionSessionEntity session = getOrFallback(trackingId);
        session.recordCommitting();
        TransmissionSessionEntity saved = transmissionSessionJpaRepository.save(session);
        activeSessions.put(trackingId, saved != null ? saved : session);
    }

    @Override
    public FileUploadGottenResult recordUploadSuccess(
            String trackingId,
            LocalFilePath sourcePath,
            RemoteFilePath targetPath,
            String fileName,
            long fileSizeBytes
    ) {
        TransmissionSessionEntity session = getOrFallback(trackingId);
        session.recordUploadSuccess(
                sourcePath != null ? sourcePath.value() : "<memory>",
                targetPath != null ? targetPath.value() : null,
                fileName,
                fileSizeBytes
        );
        activeSessions.remove(trackingId);
        TransmissionSessionEntity saved = transmissionSessionJpaRepository.save(session);
        TransmissionSessionEntity finalEntity = saved != null ? saved : session;

        return TransmissionDtoAssembler.toUploadResult(
                finalEntity.getId(),
                finalEntity.getProtocol(),
                finalEntity.getStatus().name(),
                sourcePath,
                targetPath,
                fileName,
                fileSizeBytes,
                finalEntity.getDurationMillis(),
                finalEntity.getErrorMessage()
        );
    }

    @Override
    public FileDownloadGottenResult recordDownloadSuccess(
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
        TransmissionSessionEntity saved = transmissionSessionJpaRepository.save(session);
        TransmissionSessionEntity finalEntity = saved != null ? saved : session;

        return TransmissionDtoAssembler.toDownloadResult(
                finalEntity.getId(),
                finalEntity.getProtocol(),
                finalEntity.getStatus().name(),
                sourcePath,
                targetPath,
                fileName,
                fileSizeBytes,
                finalEntity.getDurationMillis(),
                finalEntity.getErrorMessage()
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
        TransmissionSessionEntity saved = transmissionSessionJpaRepository.save(session);
        TransmissionSessionEntity finalEntity = saved != null ? saved : session;

        return TransmissionDtoAssembler.toSyncResult(
                finalEntity.getId(),
                finalEntity.getProtocol(),
                finalEntity.getStatus().name(),
                direction,
                localDir,
                remoteDir,
                transferredCount,
                deletedCount,
                finalEntity.getDurationMillis(),
                finalEntity.getErrorMessage()
        );
    }

    @Override
    public void recordFailure(String trackingId, String errorMessage) {
        TransmissionSessionEntity inMemory = activeSessions.remove(trackingId);
        if (trackingId == null || trackingId.trim().isEmpty()) {
            return;
        }

        try {
            updateStatusToFailed(trackingId, errorMessage, inMemory);
        } catch (OptimisticLockingFailureException ex) {
            // 發生樂觀鎖版本衝突，重新自 DB 載入最新版實體重試一次，確保失敗審計日誌不遺漏
            updateStatusToFailed(trackingId, errorMessage, inMemory);
        }
    }

    private void updateStatusToFailed(
            String trackingId,
            String errorMessage,
            TransmissionSessionEntity inMemoryFallback
    ) {
        Optional<TransmissionSessionEntity> fromDb = transmissionSessionJpaRepository.findById(trackingId);
        if (fromDb.isPresent()) {
            TransmissionSessionEntity entity = fromDb.get();
            entity.recordFailure(errorMessage);
            transmissionSessionJpaRepository.save(entity);
        } else if (inMemoryFallback != null) {
            inMemoryFallback.recordFailure(errorMessage);
            transmissionSessionJpaRepository.save(inMemoryFallback);
        }
    }

    @Override
    public Optional<TransmissionStatusGottenResult> getTransmissionStatus(String trackingId) {
        if (trackingId == null || trackingId.trim().isEmpty()) {
            return Optional.empty();
        }
        TransmissionSessionEntity session = transmissionSessionJpaRepository.findById(trackingId).orElse(null);
        if (session == null) {
            session = activeSessions.get(trackingId);
        }
        if (session == null) {
            return Optional.empty();
        }
        return Optional.of(TransmissionDtoAssembler.toStatusResult(
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

    @Override
    public List<ZombieSessionGottenResult> findZombieSessions(Duration threshold) {
        Instant cutoff = Instant.now().minus(threshold);
        List<TransmissionSessionEntity> zombies = transmissionSessionJpaRepository.findZombies(
                TransmissionStatus.IN_PROGRESS,
                cutoff
        );
        return zombies.stream().map(z -> new ZombieSessionGottenResult(
                z.getId(),
                z.getProtocol(),
                z.getStatus().name(),
                z.getStage(),
                z.getSourcePath(),
                z.getTargetPath(),
                z.getFileName(),
                z.getStagingPath(),
                z.getFileSizeBytes(),
                z.getServerHost(),
                z.getServerPort(),
                z.getServerUsername(),
                z.getServerPassword(),
                z.getCredentialId(),
                z.getStartedAt(),
                z.getVersion()
        )).toList();
    }

    @Override
    public void recordReconciliationSuccess(String trackingId, long fileSizeBytes) {
        activeSessions.remove(trackingId);
        transmissionSessionJpaRepository.findById(trackingId).ifPresent(entity -> {
            entity.recordReconciledSuccess(fileSizeBytes);
            transmissionSessionJpaRepository.save(entity);
        });
    }

    @Override
    public void recordReconciliationFailure(String trackingId, String errorMessage) {
        activeSessions.remove(trackingId);
        transmissionSessionJpaRepository.findById(trackingId).ifPresent(entity -> {
            entity.recordReconciledFailure(errorMessage);
            transmissionSessionJpaRepository.save(entity);
        });
    }

    /**
     * 獲取進行中實體或安全回退
     * <p>優先自資料庫查詢以獲取最新版本狀態 (防止樂觀鎖版本落後)，若 DB 無紀錄再回退至記憶體或建立新實體。</p>
     *
     * @param trackingId 傳輸追蹤唯一識別碼
     * @return TransmissionSessionEntity 實體
     */
    private TransmissionSessionEntity getOrFallback(String trackingId) {
        if (trackingId != null && !trackingId.trim().isEmpty()) {
            Optional<TransmissionSessionEntity> fromDb = transmissionSessionJpaRepository.findById(trackingId);
            if (fromDb.isPresent()) {
                return fromDb.get();
            }
            TransmissionSessionEntity existing = activeSessions.get(trackingId);
            if (existing != null) {
                return existing;
            }
        }
        String id = (trackingId != null && !trackingId.trim().isEmpty())
                ? trackingId
                : FileTransmissionId.generate().getValue();
        return TransmissionSessionEntity.start(id, "FTP");
    }
}
