package com.example.demo.application.service;

import com.example.demo.application.port.out.FileTransmissionGatewayPort;
import com.example.demo.application.port.out.TransmissionCompensationStoragePort;
import com.example.demo.application.shared.vo.CompensationAction;
import com.example.demo.application.shared.vo.CompensationStatus;
import com.example.demo.application.shared.vo.CompensationTask;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Saga 補償交易應用服務單元測試 (TransmissionCompensationServiceTest)
 * <p>
 * 遵循架構與測試規範：
 * 1. 涵蓋 ProcessCompensationUseCase 正常業務流程與錯誤重試分支；
 * 2. 測試方法均標註 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TransmissionCompensationServiceTest {

    @Mock
    private TransmissionCompensationStoragePort compensationStoragePort;

    @Mock
    private FileTransmissionGatewayPort fileTransmissionGatewayPort;

    private TransmissionCompensationService compensationService;

    @BeforeEach
    void setUp() {
        compensationService = new TransmissionCompensationService(
                compensationStoragePort,
                fileTransmissionGatewayPort
        );
    }

    /**
     * Feature: Saga 補償交易排程處理
     * Scenario: 成功清理遠端殘存之暫存檔案並標記任務 SUCCESS
     * Given: 補償儲存埠回傳一筆 PENDING 的 DELETE_REMOTE_STAGING 任務，Gateway 探測檔案存在且刪除成功
     * When: 執行 processPendingCompensations
     * Then: 成功呼叫 Gateway deleteFile，並將任務狀態更新為 SUCCESS，回傳處理成功數 1
     */
    @Test
    @DisplayName("情境：成功執行遠端暫存檔清理補償，並標記任務狀態為 SUCCESS")
    void testProcessPendingCompensationsSuccess() {
        TransmissionServerConfig config = TransmissionServerConfig.of(
                "sftp.example.com",
                22,
                TransmissionProtocol.SFTP,
                "sftpuser",
                "password"
        );

        String remotePath = "/data/.staging.file.txt";
        Instant now = Instant.now();

        CompensationTask pendingTask = new CompensationTask(
                1L,
                "track-comp-001",
                CompensationAction.DELETE_REMOTE_STAGING,
                config,
                remotePath,
                0,
                5,
                CompensationStatus.PENDING,
                null,
                now,
                now,
                now
        );

        when(compensationStoragePort.findPendingRetries(any())).thenReturn(List.of(pendingTask));
        when(fileTransmissionGatewayPort.existsFile(eq(config), eq(RemoteFilePath.of(remotePath)))).thenReturn(true);
        when(fileTransmissionGatewayPort.deleteFile(eq(config), eq(RemoteFilePath.of(remotePath)))).thenReturn(true);

        int processed = compensationService.processPendingCompensations();

        assertEquals(1, processed);
        verify(fileTransmissionGatewayPort).deleteFile(eq(config), eq(RemoteFilePath.of(remotePath)));

        ArgumentCaptor<CompensationTask> taskCaptor = ArgumentCaptor.forClass(CompensationTask.class);
        verify(compensationStoragePort).updateCompensation(taskCaptor.capture());
        assertEquals(CompensationStatus.SUCCESS, taskCaptor.getValue().status());
    }

    /**
     * Feature: Saga 補償交易重試與退避
     * Scenario: 遠端刪除失敗時，累計 retryCount 並依指數退避排定下次重試
     * Given: Gateway 刪除暫存檔時返回 false 或拋出異常
     * When: 執行 processPendingCompensations
     * Then: retryCount 遞增為 1，status 更新為 RETRYING，nextRetryAt 推遲
     */
    @Test
    @DisplayName("情境：補償執行失敗時，更新狀態為 RETRYING 並依退避推遲下次重試時間")
    void testProcessPendingCompensationsRetryFailure() {
        TransmissionServerConfig config = TransmissionServerConfig.of(
                "ftp.example.com",
                21,
                TransmissionProtocol.FTP,
                "ftpuser",
                "password"
        );

        String remotePath = "/temp/.orphan.txt";
        Instant now = Instant.now();

        CompensationTask pendingTask = new CompensationTask(
                2L,
                "track-comp-002",
                CompensationAction.DELETE_REMOTE_STAGING,
                config,
                remotePath,
                0,
                5,
                CompensationStatus.PENDING,
                null,
                now,
                now,
                now
        );

        when(compensationStoragePort.findPendingRetries(any())).thenReturn(List.of(pendingTask));
        when(fileTransmissionGatewayPort.existsFile(eq(config), eq(RemoteFilePath.of(remotePath)))).thenReturn(true);
        when(fileTransmissionGatewayPort.deleteFile(eq(config), eq(RemoteFilePath.of(remotePath)))).thenReturn(false);

        int processed = compensationService.processPendingCompensations();

        assertEquals(0, processed);

        ArgumentCaptor<CompensationTask> taskCaptor = ArgumentCaptor.forClass(CompensationTask.class);
        verify(compensationStoragePort).updateCompensation(taskCaptor.capture());

        CompensationTask updated = taskCaptor.getValue();
        assertEquals(1, updated.retryCount());
        assertEquals(CompensationStatus.RETRYING, updated.status());
        assertTrue(updated.nextRetryAt().isAfter(now));
    }

    /**
     * Feature: Saga 補償交易重試上限
     * Scenario: 補償任務達到最大重試次數上限時放棄並標記 ABANDONED
     * Given: 當前已重試 4 次，maxRetries 為 5，再次執行仍失敗
     * When: 執行 processPendingCompensations
     * Then: retryCount 達到 5，狀態更新為 ABANDONED
     */
    @Test
    @DisplayName("情境：補償重試達到上限時標記為 ABANDONED")
    void testProcessPendingCompensationsMaxRetriesExceeded() {
        TransmissionServerConfig config = TransmissionServerConfig.of(
                "sftp.example.com",
                22,
                TransmissionProtocol.SFTP,
                "sftpuser",
                "password"
        );

        String remotePath = "/bad/.staging.log";
        Instant now = Instant.now();

        CompensationTask pendingTask = new CompensationTask(
                3L,
                "track-comp-003",
                CompensationAction.DELETE_REMOTE_STAGING,
                config,
                remotePath,
                4,
                5,
                CompensationStatus.RETRYING,
                "Previous error",
                now,
                now,
                now
        );

        when(compensationStoragePort.findPendingRetries(any())).thenReturn(List.of(pendingTask));
        when(fileTransmissionGatewayPort.existsFile(eq(config), eq(RemoteFilePath.of(remotePath)))).thenReturn(true);
        when(fileTransmissionGatewayPort.deleteFile(eq(config), eq(RemoteFilePath.of(remotePath))))
                .thenThrow(new RuntimeException("連線持續中斷"));

        int processed = compensationService.processPendingCompensations();

        assertEquals(0, processed);

        ArgumentCaptor<CompensationTask> taskCaptor = ArgumentCaptor.forClass(CompensationTask.class);
        verify(compensationStoragePort).updateCompensation(taskCaptor.capture());

        CompensationTask updated = taskCaptor.getValue();
        assertEquals(5, updated.retryCount());
        assertEquals(CompensationStatus.ABANDONED, updated.status());
    }

    /**
     * Feature: 補償交易無待處理任務
     * Scenario: 當無任何待處理之補償任務時正常略過
     * Given: 補償儲存庫回傳空清單
     * When: 執行 processPendingCompensations
     * Then: 回傳 0 且不呼叫 Gateway
     */
    @Test
    @DisplayName("情境：無待處理任務時正常返回 0")
    void testProcessPendingCompensationsEmpty() {
        when(compensationStoragePort.findPendingRetries(any())).thenReturn(Collections.emptyList());

        int processed = compensationService.processPendingCompensations();

        assertEquals(0, processed);
    }
}
