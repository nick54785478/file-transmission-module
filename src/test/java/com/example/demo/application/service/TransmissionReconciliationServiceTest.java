package com.example.demo.application.service;

import com.example.demo.application.port.out.FileTransmissionGatewayPort;
import com.example.demo.application.port.out.FileTransmissionTrackerPort;
import com.example.demo.application.port.out.ServerCredentialStoragePort;
import com.example.demo.application.shared.dto.TransmissionReconciliationGottenResult;
import com.example.demo.application.shared.dto.ZombieSessionGottenResult;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.example.demo.application.shared.vo.TransmissionStage;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 傳輸對帳與自癒應用服務單元測試 (TransmissionReconciliationServiceTest)
 * <p>
 * 遵循架構與測試規範：
 * 1. 涵蓋 ReconcileTransmissionUseCase 各類對帳場景與邊界分支；
 * 2. 測試方法均標註 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TransmissionReconciliationServiceTest {

    @Mock
    private FileTransmissionTrackerPort trackerPort;

    @Mock
    private FileTransmissionGatewayPort gatewayPort;

    @Mock
    private ServerCredentialStoragePort credentialStoragePort;

    private TransmissionReconciliationService reconciliationService;

    @BeforeEach
    void setUp() {
        reconciliationService = new TransmissionReconciliationService(
                trackerPort,
                gatewayPort,
                credentialStoragePort
        );
    }

    /**
     * Feature: 背景對帳自癒引擎
     * Scenario: 殭屍任務的遠端正式檔案完整存在且大小相符，自動晉升為 COMPLETED 終態
     * Given: 掃描發現一筆超時殭屍任務，遠端正式目標檔案存在且大小與預期 1024 bytes 完全相符
     * When: 執行 reconcile 對帳
     * Then: 調用 recordReconciliationSuccess 標記自癒成功，自癒統計 +1
     */
    @Test
    @DisplayName("情境：遠端正式檔案完整存在，自動自癒晉升為 COMPLETED")
    void testReconcileSelfHealsWhenRemoteTargetFileExists() {
        ZombieSessionGottenResult zombie = new ZombieSessionGottenResult(
                "zombie-001",
                "SFTP",
                "IN_PROGRESS",
                TransmissionStage.COMMITTING,
                "/local/data.csv",
                "/remote/reports",
                "data.csv",
                "/remote/reports/.data.csv.staging.zombie-001",
                1024L,
                "192.168.1.50",
                22,
                "sftpuser",
                "secret",
                null,
                Instant.now().minus(Duration.ofMinutes(40)),
                0L
        );

        when(trackerPort.findZombieSessions(any())).thenReturn(List.of(zombie));

        RemoteFilePath targetFile = RemoteFilePath.of("/remote/reports/data.csv");
        when(gatewayPort.existsFile(any(), eq(targetFile))).thenReturn(true);
        when(gatewayPort.getRemoteFileSize(any(), eq(targetFile))).thenReturn(1024L);

        TransmissionReconciliationGottenResult result = reconciliationService.reconcile(Duration.ofMinutes(30));

        assertEquals(1, result.totalScanned());
        assertEquals(1, result.selfHealedCount());
        assertEquals(0, result.purgedCount());
        assertEquals(0, result.failedCount());

        verify(trackerPort).recordReconciliationSuccess(eq("zombie-001"), eq(1024L));
    }

    /**
     * Feature: 背景對帳自癒引擎
     * Scenario: 遠端正式檔案不存在，但遺留 .staging 暫存檔，主動清理並標記 FAILED
     * Given: 正式檔不存在，但 stagingPath 存在於遠端伺服器
     * When: 執行 reconcile 對帳
     * Then: 調用 Gateway deleteFile 清理暫存檔，調用 recordReconciliationFailure 標記逾時清理失敗
     */
    @Test
    @DisplayName("情境：正式檔案不存在但遺留暫存檔，主動清除暫存檔並標記為 FAILED")
    void testReconcilePurgesStagingFileWhenTargetMissing() {
        ZombieSessionGottenResult zombie = new ZombieSessionGottenResult(
                "zombie-002",
                "FTP",
                "IN_PROGRESS",
                TransmissionStage.STAGING,
                "/local/temp.xml",
                "/remote/inbox",
                "temp.xml",
                "/remote/inbox/.temp.xml.staging.zombie-002",
                2048L,
                "ftp.company.com",
                21,
                "ftpuser",
                "ftppass",
                null,
                Instant.now().minus(Duration.ofMinutes(45)),
                0L
        );

        when(trackerPort.findZombieSessions(any())).thenReturn(List.of(zombie));

        RemoteFilePath targetFile = RemoteFilePath.of("/remote/inbox/temp.xml");
        RemoteFilePath stagingFile = RemoteFilePath.of("/remote/inbox/.temp.xml.staging.zombie-002");

        when(gatewayPort.existsFile(any(), eq(targetFile))).thenReturn(false);
        when(gatewayPort.existsFile(any(), eq(stagingFile))).thenReturn(true);

        TransmissionReconciliationGottenResult result = reconciliationService.reconcile(Duration.ofMinutes(30));

        assertEquals(1, result.totalScanned());
        assertEquals(0, result.selfHealedCount());
        assertEquals(1, result.purgedCount());
        assertEquals(0, result.failedCount());

        verify(gatewayPort).deleteFile(any(), eq(stagingFile));
        verify(trackerPort).recordReconciliationFailure(eq("zombie-002"), anyString());
    }

    /**
     * Feature: 背景對帳自癒引擎
     * Scenario: 正式檔案與暫存檔皆不存在，直接標記為終態 FAILED
     * Given: 遠端 Gateway existsFile 對正式檔與暫存檔均回傳 false
     * When: 執行 reconcile 對帳
     * Then: 調用 recordReconciliationFailure 標記為異常中斷失敗
     */
    @Test
    @DisplayName("情境：正式檔與暫存檔均不存在，直接標記為終態 FAILED")
    void testReconcileMarksFailedWhenNoFilesExist() {
        ZombieSessionGottenResult zombie = new ZombieSessionGottenResult(
                "zombie-003",
                "SFTP",
                "IN_PROGRESS",
                TransmissionStage.STAGING,
                "/local/test.dat",
                "/remote/outbox",
                "test.dat",
                null,
                512L,
                "sftp.company.com",
                22,
                "sftpuser",
                "secret",
                null,
                Instant.now().minus(Duration.ofMinutes(60)),
                0L
        );

        when(trackerPort.findZombieSessions(any())).thenReturn(List.of(zombie));

        RemoteFilePath targetFile = RemoteFilePath.of("/remote/outbox/test.dat");
        when(gatewayPort.existsFile(any(), eq(targetFile))).thenReturn(false);

        TransmissionReconciliationGottenResult result = reconciliationService.reconcile(Duration.ofMinutes(30));

        assertEquals(1, result.totalScanned());
        assertEquals(0, result.selfHealedCount());
        assertEquals(0, result.purgedCount());
        assertEquals(1, result.failedCount());

        verify(trackerPort).recordReconciliationFailure(eq("zombie-003"), anyString());
    }

    /**
     * Feature: 樂觀鎖並發防護機制
     * Scenario: 當對帳引擎更新與主執行緒發生版本碰撞時，安全捕獲 OptimisticLockingFailureException 並略過
     * Given: trackerPort.recordReconciliationSuccess 拋出 OptimisticLockingFailureException
     * When: 執行 reconcile 對帳
     * Then: 異常被安全捕獲，不中斷整體對帳流程
     */
    @Test
    @DisplayName("情境：對帳更新發生樂觀鎖衝突時，安全略過不拋出異常")
    void testReconcileHandlesOptimisticLockExceptionGracefully() {
        ZombieSessionGottenResult zombie = new ZombieSessionGottenResult(
                "zombie-004",
                "SFTP",
                "IN_PROGRESS",
                TransmissionStage.COMMITTING,
                "/local/data.txt",
                "/remote/data",
                "data.txt",
                null,
                100L,
                "192.168.1.1",
                22,
                "user",
                "pass",
                null,
                Instant.now().minus(Duration.ofMinutes(35)),
                0L
        );

        when(trackerPort.findZombieSessions(any())).thenReturn(List.of(zombie));

        RemoteFilePath targetFile = RemoteFilePath.of("/remote/data/data.txt");
        when(gatewayPort.existsFile(any(), eq(targetFile))).thenReturn(true);
        when(gatewayPort.getRemoteFileSize(any(), eq(targetFile))).thenReturn(100L);

        doThrow(new OptimisticLockingFailureException("版本衝突"))
                .when(trackerPort)
                .recordReconciliationSuccess(eq("zombie-004"), anyLong());

        TransmissionReconciliationGottenResult result = reconciliationService.reconcile(Duration.ofMinutes(30));

        assertEquals(1, result.totalScanned());
        assertEquals(0, result.selfHealedCount());
    }

    /**
     * Feature: 無殭屍任務情境
     * Scenario: 掃描無任何超時任務時直接回傳空結果
     * Given: trackerPort.findZombieSessions 回傳空清單
     * When: 執行 reconcile 對帳
     * Then: 回傳全部統計為 0 之空結果
     */
    @Test
    @DisplayName("情境：無任何殭屍任務時返回空結果")
    void testReconcileEmpty() {
        when(trackerPort.findZombieSessions(any())).thenReturn(Collections.emptyList());

        TransmissionReconciliationGottenResult result = reconciliationService.reconcile(Duration.ofMinutes(30));

        assertEquals(0, result.totalScanned());
        verify(gatewayPort, never()).existsFile(any(), any());
    }
}
