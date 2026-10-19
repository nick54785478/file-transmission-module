package com.example.demo.infrastructure.adapter;

import com.example.demo.application.shared.dto.DirectorySyncGottenResult;
import com.example.demo.application.shared.dto.FileDownloadGottenResult;
import com.example.demo.application.shared.dto.FileUploadGottenResult;
import com.example.demo.application.shared.dto.TransmissionStatusGottenResult;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.example.demo.infrastructure.persistence.transmission.entity.TransmissionSessionEntity;
import com.example.demo.infrastructure.persistence.transmission.vo.TransmissionStatus;
import com.example.demo.infrastructure.persistence.transmission.repository.TransmissionSessionJpaRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 檔案傳輸追蹤適配器測試 (FileTransmissionTrackerAdapterTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 涵蓋 Adapter 實作方法之狀態流轉、結果 DTO 組裝與 JPA 持久化呼叫驗證；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class FileTransmissionTrackerAdapterTest {

    @Mock
    private TransmissionSessionJpaRepository transmissionSessionJpaRepository;

    private FileTransmissionTrackerAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FileTransmissionTrackerAdapter(transmissionSessionJpaRepository);
    }

    /**
     * Feature: 傳輸作業生命週期追蹤與持久化
     * Scenario: 啟動傳輸追蹤並成功記錄檔案上傳完成
     * Given: 準備有效的伺服器連線配置與檔案資訊
     * When: 依序呼叫 startTracking 與 recordUploadSuccess
     * Then: 回傳狀態為 COMPLETED 之結果 DTO 且驗證 JPA Repository 持久化呼叫
     */
    @Test
    @DisplayName("情境：啟動傳輸追蹤並記錄上傳成功")
    void testRecordUploadSuccess() {
        TransmissionServerConfig config = TransmissionServerConfig.of(
                "sftp.example.com",
                22,
                TransmissionProtocol.SFTP,
                "user",
                "pass"
        );

        String trackingId = adapter.startTracking(config);
        assertNotNull(trackingId);

        FileUploadGottenResult result = adapter.recordUploadSuccess(
                trackingId,
                LocalFilePath.of("/local/file.txt"),
                RemoteFilePath.of("/remote/data"),
                "file.txt",
                1024L
        );

        assertNotNull(result);
        assertEquals(trackingId, result.transmissionId());
        assertEquals("SFTP", result.protocol());
        assertEquals("COMPLETED", result.status());
        assertEquals("file.txt", result.fileName());
        assertEquals(1024L, result.fileSizeBytes());
        assertTrue(result.durationMillis() >= 0);
        assertNull(result.errorMessage());

        // 驗證已呼叫 JPA 持久化儲存
        verify(transmissionSessionJpaRepository, atLeastOnce()).save(any(TransmissionSessionEntity.class));
    }

    /**
     * Feature: 傳輸作業生命週期追蹤與持久化
     * Scenario: 啟動傳輸追蹤並成功記錄檔案下載完成
     * Given: 準備有效的伺服器連線配置與下載資訊
     * When: 依序呼叫 startTracking 與 recordDownloadSuccess
     * Then: 回傳狀態為 COMPLETED 之結果 DTO 並驗證 JPA 保存
     */
    @Test
    @DisplayName("情境：啟動傳輸追蹤並記錄下載成功")
    void testRecordDownloadSuccess() {
        TransmissionServerConfig config = TransmissionServerConfig.of(
                "ftp.example.com",
                21,
                TransmissionProtocol.FTP,
                "user",
                "pass"
        );

        String trackingId = adapter.startTracking(config);

        FileDownloadGottenResult result = adapter.recordDownloadSuccess(
                trackingId,
                RemoteFilePath.of("/remote/file.txt"),
                LocalFilePath.of("/local/file.txt"),
                "file.txt",
                2048L
        );

        assertNotNull(result);
        assertEquals(trackingId, result.transmissionId());
        assertEquals("FTP", result.protocol());
        assertEquals("COMPLETED", result.status());
        assertEquals("file.txt", result.fileName());
        assertEquals(2048L, result.fileSizeBytes());

        verify(transmissionSessionJpaRepository, atLeastOnce()).save(any(TransmissionSessionEntity.class));
    }

    /**
     * Feature: 傳輸作業生命週期追蹤與持久化
     * Scenario: 啟動傳輸追蹤並成功記錄目錄同步完成
     * Given: 準備有效的伺服器連線配置與同步參數
     * When: 依序呼叫 startTracking 與 recordSyncSuccess
     * Then: 回傳狀態為 COMPLETED 之目錄同步結果 DTO 並驗證 JPA 保存
     */
    @Test
    @DisplayName("情境：啟動傳輸追蹤並記錄目錄同步成功")
    void testRecordSyncSuccess() {
        TransmissionServerConfig config = TransmissionServerConfig.of(
                "ftps.example.com",
                990,
                TransmissionProtocol.FTPS,
                "user",
                "pass"
        );

        String trackingId = adapter.startTracking(config);

        DirectorySyncGottenResult result = adapter.recordSyncSuccess(
                trackingId,
                SyncDirection.LOCAL_TO_REMOTE,
                LocalFilePath.of("/local/dir"),
                RemoteFilePath.of("/remote/dir"),
                10,
                2
        );

        assertNotNull(result);
        assertEquals(trackingId, result.transmissionId());
        assertEquals("FTPS", result.protocol());
        assertEquals("COMPLETED", result.status());
        assertEquals("LOCAL_TO_REMOTE", result.direction());
        assertEquals(10, result.filesTransferredCount());
        assertEquals(2, result.filesDeletedCount());

        verify(transmissionSessionJpaRepository, atLeastOnce()).save(any(TransmissionSessionEntity.class));
    }

    /**
     * Feature: 傳輸作業生命週期追蹤與持久化
     * Scenario: 傳輸失敗時正確記錄錯誤原因並持久化失敗狀態
     * Given: 啟動追蹤後發生異常
     * When: 呼叫 recordFailure 方法
     * Then: 追蹤 session 被正確移除並持久化 FAILED 狀態
     */
    @Test
    @DisplayName("情境：記錄傳輸作業失敗")
    void testRecordFailure() {
        TransmissionServerConfig config = TransmissionServerConfig.of(
                "ftp.example.com",
                21,
                TransmissionProtocol.FTP,
                "user",
                "pass"
        );

        String trackingId = adapter.startTracking(config);
        adapter.recordFailure(trackingId, "網路連線逾時");

        ArgumentCaptor<TransmissionSessionEntity> captor = ArgumentCaptor.forClass(TransmissionSessionEntity.class);
        verify(transmissionSessionJpaRepository, atLeastOnce()).save(captor.capture());

        TransmissionSessionEntity lastSaved = captor.getValue();
        assertEquals(TransmissionStatus.FAILED, lastSaved.getStatus());
        assertEquals("網路連線逾時", lastSaved.getErrorMessage());

        // 再次呼叫 recordUploadSuccess 應回退至新 session 而不拋出未捕捉異常
        FileUploadGottenResult result = adapter.recordUploadSuccess(
                trackingId,
                LocalFilePath.of("/local/file.txt"),
                RemoteFilePath.of("/remote/data"),
                "file.txt",
                512L
        );

        assertNotNull(result);
        assertEquals(trackingId, result.transmissionId());
    }

    /**
     * Feature: 傳輸作業生命週期追蹤與持久化
     * Scenario: 當 Session 已不存在但資料庫存在時，recordFailure 正確更新資料庫實體狀態
     * Given: 記憶體無該 trackingId 但 JPA Repository 能查得歷史實體
     * When: 呼叫 recordFailure
     * Then: 資料庫實體狀態被更新為 FAILED 且保存
     */
    @Test
    @DisplayName("情境：非現存 session 透過 JPA 更新為 FAILED")
    void testRecordFailureFallbackToJpa() {
        String trackingId = "non-existing-session-id";
        TransmissionSessionEntity entity = TransmissionSessionEntity.start(trackingId, "SFTP");
        when(transmissionSessionJpaRepository.findById(trackingId)).thenReturn(Optional.of(entity));

        adapter.recordFailure(trackingId, "伺服器拒絕連線");

        assertEquals(TransmissionStatus.FAILED, entity.getStatus());
        assertEquals("伺服器拒絕連線", entity.getErrorMessage());
        assertNotNull(entity.getCompletedAt());
        verify(transmissionSessionJpaRepository).save(entity);
    }

    /**
     * Feature: 傳輸作業生命週期追蹤與持久化
     * Scenario: 當記錄傳輸失敗時遭遇樂觀鎖版本衝突，能自動重新查詢最新資料庫版本重試更新
     * Given: 首次保存實體時拋出 OptimisticLockingFailureException，隨後再次查詢並保存最新實體
     * When: 呼叫 recordFailure
     * Then: 成功捕獲版本衝突並重試，最新實體之狀態被持久化為 FAILED
     */
    @Test
    @DisplayName("情境：記錄傳輸失敗遇樂觀鎖衝突時自動重試更新")
    void testRecordFailureRetryOnOptimisticLockException() {
        String trackingId = "conflict-session-id";
        TransmissionSessionEntity staleEntity = TransmissionSessionEntity.start(trackingId, "SFTP");
        TransmissionSessionEntity freshEntity = TransmissionSessionEntity.start(trackingId, "SFTP");

        when(transmissionSessionJpaRepository.findById(trackingId))
                .thenReturn(Optional.of(staleEntity))
                .thenReturn(Optional.of(freshEntity));
        when(transmissionSessionJpaRepository.save(any(TransmissionSessionEntity.class)))
                .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("Version conflict"))
                .thenReturn(freshEntity);

        adapter.recordFailure(trackingId, "傳輸連線被重置");

        assertEquals(TransmissionStatus.FAILED, freshEntity.getStatus());
        assertEquals("傳輸連線被重置", freshEntity.getErrorMessage());
        verify(transmissionSessionJpaRepository, org.mockito.Mockito.times(2)).save(any(TransmissionSessionEntity.class));
    }

    /**
     * Feature: 傳輸狀態查詢
     * Scenario: 成功從記憶體與資料庫查詢傳輸狀態
     * Given: 準備記憶體或資料庫存在之傳輸作業紀錄
     * When: 呼叫 getTransmissionStatus
     * Then: 正確回傳封裝結果之 Optional DTO
     */
    @Test
    @DisplayName("情境：成功查詢進行中與已持久化之傳輸狀態")
    void testGetTransmissionStatus() {
        TransmissionServerConfig config = TransmissionServerConfig.of(
                "sftp.example.com",
                22,
                TransmissionProtocol.SFTP,
                "user",
                "pass"
        );
        String trackingId = adapter.startTracking(config);

        // 1. 從記憶體查詢進行中狀態
        Optional<TransmissionStatusGottenResult> inProgressResult = adapter.getTransmissionStatus(trackingId);
        assertTrue(inProgressResult.isPresent());
        assertEquals(trackingId, inProgressResult.get().transmissionId());
        assertEquals("IN_PROGRESS", inProgressResult.get().status());

        // 2. 從資料庫查詢歷史紀錄
        String historicalId = "hist-001";
        TransmissionSessionEntity historicalEntity = TransmissionSessionEntity.reconstitute(
                historicalId,
                "FTP",
                TransmissionStatus.COMPLETED,
                "/local/a.txt",
                "/remote/a.txt",
                "a.txt",
                512L,
                40L,
                null,
                java.time.Instant.now(),
                java.time.Instant.now()
        );
        when(transmissionSessionJpaRepository.findById(historicalId)).thenReturn(Optional.of(historicalEntity));

        Optional<TransmissionStatusGottenResult> histResult = adapter.getTransmissionStatus(historicalId);
        assertTrue(histResult.isPresent());
        assertEquals(historicalId, histResult.get().transmissionId());
        assertEquals("COMPLETED", histResult.get().status());
        assertEquals(512L, histResult.get().fileSizeBytes());

        // 3. 查無紀錄
        Optional<TransmissionStatusGottenResult> emptyResult = adapter.getTransmissionStatus("unknown-id");
        assertTrue(emptyResult.isEmpty());
    }
}

