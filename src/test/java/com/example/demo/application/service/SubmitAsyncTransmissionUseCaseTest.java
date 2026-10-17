package com.example.demo.application.service;

import com.example.demo.application.port.out.FileTransmissionGatewayPort;
import com.example.demo.application.port.out.FileTransmissionTrackerPort;
import com.example.demo.application.shared.command.DownloadFileCommand;
import com.example.demo.application.shared.command.SyncDirectoryCommand;
import com.example.demo.application.shared.command.UploadFileCommand;
import com.example.demo.application.shared.dto.FileTransmissionSubmittedResult;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 非同步傳輸長任務提交使用案例測試 (SubmitAsyncTransmissionUseCaseTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 涵蓋 SubmitUploadFileUseCase, SubmitDownloadFileUseCase, SubmitSyncDirectoryUseCase 流程驗證；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SubmitAsyncTransmissionUseCaseTest {

    @Mock
    private FileTransmissionGatewayPort fileTransmissionGatewayPort;

    @Mock
    private FileTransmissionTrackerPort fileTransmissionTrackerPort;

    private FileTransmissionCommandService commandService;

    @BeforeEach
    void setUp() {
        // 使用同步執行器 (Runnable::run) 使背景非同步邏輯在單元測試中同步執行以利驗證
        commandService = new FileTransmissionCommandService(
                fileTransmissionGatewayPort,
                fileTransmissionTrackerPort,
                Runnable::run
        );
    }

    /**
     * Feature: 非同步長任務提交
     * Scenario: 提交檔案上傳任務並立即取得 trackingId
     * Given: 準備有效的伺服器連線配置與本機檔案
     * When: 呼叫 submitUploadFile 方法
     * Then: 立即回傳狀態為 IN_PROGRESS 之提交結果 DTO，並在背景完成傳輸與追蹤紀錄
     */
    @Test
    @DisplayName("情境：提交非同步檔案上傳任務")
    void testSubmitUploadFile() throws IOException {
        Path tempFile = Files.createTempFile("async-upload-test-", ".txt");
        Files.writeString(tempFile, "Async Upload Content");

        TransmissionServerConfig config = TransmissionServerConfig.of(
                "sftp.example.com",
                22,
                TransmissionProtocol.SFTP,
                "user",
                "pass"
        );
        UploadFileCommand command = new UploadFileCommand(
                config,
                LocalFilePath.of(tempFile.toString()),
                RemoteFilePath.of("/remote/data"),
                "upload.txt"
        );

        when(fileTransmissionTrackerPort.startTracking(config)).thenReturn("task-async-001");

        FileTransmissionSubmittedResult result = commandService.submitUploadFile(command);

        assertNotNull(result);
        assertEquals("task-async-001", result.trackingId());
        assertEquals("SFTP", result.protocol());
        assertEquals("IN_PROGRESS", result.status());
        assertNotNull(result.submittedAt());

        // 驗證背景執行緒已觸發 Gateway 與 Tracker
        verify(fileTransmissionGatewayPort).upload(eq(config), any(), any(), eq("upload.txt"));
        verify(fileTransmissionTrackerPort).recordUploadSuccess(eq("task-async-001"), any(), any(), eq("upload.txt"), anyLong());

        Files.deleteIfExists(tempFile);
    }

    /**
     * Feature: 非同步長任務提交
     * Scenario: 提交檔案下載任務並立即取得 trackingId
     * Given: 準備有效的伺服器連線配置與下載路徑
     * When: 呼叫 submitDownloadFile 方法
     * Then: 立即回傳狀態為 IN_PROGRESS 之提交結果 DTO，並在背景完成下載與追蹤紀錄
     */
    @Test
    @DisplayName("情境：提交非同步檔案下載任務")
    void testSubmitDownloadFile() {
        TransmissionServerConfig config = TransmissionServerConfig.of(
                "ftp.example.com",
                21,
                TransmissionProtocol.FTP,
                "user",
                "pass"
        );
        DownloadFileCommand command = new DownloadFileCommand(
                config,
                RemoteFilePath.of("/remote/data/download.txt"),
                LocalFilePath.of("/local/download.txt")
        );

        when(fileTransmissionTrackerPort.startTracking(config)).thenReturn("task-async-002");

        FileTransmissionSubmittedResult result = commandService.submitDownloadFile(command);

        assertNotNull(result);
        assertEquals("task-async-002", result.trackingId());
        assertEquals("FTP", result.protocol());
        assertEquals("IN_PROGRESS", result.status());

        verify(fileTransmissionGatewayPort).download(eq(config), any(), any());
        verify(fileTransmissionTrackerPort).recordDownloadSuccess(eq("task-async-002"), any(), any(), eq("download.txt"), anyLong());
    }

    /**
     * Feature: 非同步長任務提交
     * Scenario: 提交目錄同步任務並立即取得 trackingId
     * Given: 準備有效的伺服器連線配置與目錄參數
     * When: 呼叫 submitSyncDirectory 方法
     * Then: 立即回傳狀態為 IN_PROGRESS 之提交結果 DTO，並在背景完成目錄同步與追蹤紀錄
     */
    @Test
    @DisplayName("情境：提交非同步目錄同步任務")
    void testSubmitSyncDirectory() {
        TransmissionServerConfig config = TransmissionServerConfig.of(
                "ftps.example.com",
                990,
                TransmissionProtocol.FTPS,
                "user",
                "pass"
        );
        SyncDirectoryCommand command = new SyncDirectoryCommand(
                config,
                LocalFilePath.of("/local/dir"),
                RemoteFilePath.of("/remote/dir"),
                SyncDirection.LOCAL_TO_REMOTE,
                false
        );

        when(fileTransmissionTrackerPort.startTracking(config)).thenReturn("task-async-003");
        when(fileTransmissionGatewayPort.syncDirectory(any(), any(), any(), any(), eq(false))).thenReturn(8);

        FileTransmissionSubmittedResult result = commandService.submitSyncDirectory(command);

        assertNotNull(result);
        assertEquals("task-async-003", result.trackingId());
        assertEquals("FTPS", result.protocol());
        assertEquals("IN_PROGRESS", result.status());

        verify(fileTransmissionGatewayPort).syncDirectory(eq(config), any(), any(), eq(SyncDirection.LOCAL_TO_REMOTE), eq(false));
        verify(fileTransmissionTrackerPort).recordSyncSuccess(eq("task-async-003"), eq(SyncDirection.LOCAL_TO_REMOTE), any(), any(), eq(8), eq(0));
    }
}
