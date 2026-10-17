package com.example.demo.application.service;

import com.example.demo.application.shared.command.SyncDirectoryCommand;
import com.example.demo.application.shared.dto.DirectorySyncGottenResult;
import com.example.demo.application.port.out.FileTransmissionGatewayPort;
import com.example.demo.application.port.out.FileTransmissionTrackerPort;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.example.demo.application.shared.exception.FileTransmissionException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 目錄同步使用案例測試 (SyncDirectoryUseCaseTest)
 * <p>
 * 遵循測試規範：
 * 1. 涵蓋 UseCase 正常業務流程與錯誤異常分支驗證；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SyncDirectoryUseCaseTest {

    @Mock
    private FileTransmissionGatewayPort fileTransmissionGatewayPort;

    @Mock
    private FileTransmissionTrackerPort fileTransmissionTrackerPort;

    private FileTransmissionCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new FileTransmissionCommandService(fileTransmissionGatewayPort, fileTransmissionTrackerPort, Runnable::run);
    }

    /**
     * Feature: 目錄同步業務流程
     * Scenario: 成功將本機目錄同步至遠端 FTPS 伺服器
     * Given: 準備有效之連線配置、本機目錄與遠端目標目錄
     * When: 執行 SyncDirectoryUseCase 的 syncDirectory 方法
     * Then: Outbound Port 成功被呼叫、回傳同步檔案數與成功狀態 DTO
     */
    @Test
    @DisplayName("情境：成功同步本機目錄至遠端 FTPS 主機")
    void testSyncLocalToRemoteSuccess() throws IOException {
        Path tempDir = Files.createTempDirectory("test-sync-dir-");

        try {
            TransmissionServerConfig serverConfig = TransmissionServerConfig.of(
                    "ftps.example.com",
                    990,
                    TransmissionProtocol.FTPS,
                    "ftpsuser",
                    "ftpspass"
            );

            SyncDirectoryCommand command = new SyncDirectoryCommand(
                    serverConfig,
                    LocalFilePath.of(tempDir.toString()),
                    RemoteFilePath.of("/remote/backup"),
                    SyncDirection.LOCAL_TO_REMOTE,
                    true
            );

            when(fileTransmissionTrackerPort.startTracking(eq(serverConfig)))
                    .thenReturn("track-sync-123");

            when(fileTransmissionGatewayPort.syncDirectory(
                    eq(serverConfig),
                    any(LocalFilePath.class),
                    eq(RemoteFilePath.of("/remote/backup")),
                    eq(SyncDirection.LOCAL_TO_REMOTE),
                    eq(true)
            )).thenReturn(5);

            DirectorySyncGottenResult mockResult = new DirectorySyncGottenResult(
                    "track-sync-123",
                    "FTPS",
                    "COMPLETED",
                    "LOCAL_TO_REMOTE",
                    tempDir.toString(),
                    "/remote/backup",
                    5,
                    0,
                    120L,
                    null
            );

            when(fileTransmissionTrackerPort.recordSyncSuccess(
                    eq("track-sync-123"),
                    eq(SyncDirection.LOCAL_TO_REMOTE),
                    any(LocalFilePath.class),
                    eq(RemoteFilePath.of("/remote/backup")),
                    eq(5),
                    anyInt()
            )).thenReturn(mockResult);

            DirectorySyncGottenResult result = commandService.syncDirectory(command);

            assertNotNull(result);
            assertEquals("COMPLETED", result.status());
            assertEquals("FTPS", result.protocol());
            assertEquals("LOCAL_TO_REMOTE", result.direction());
            assertEquals(5, result.filesTransferredCount());

            verify(fileTransmissionTrackerPort).startTracking(eq(serverConfig));
            verify(fileTransmissionGatewayPort).syncDirectory(
                    eq(serverConfig),
                    any(LocalFilePath.class),
                    eq(RemoteFilePath.of("/remote/backup")),
                    eq(SyncDirection.LOCAL_TO_REMOTE),
                    eq(true)
            );
            verify(fileTransmissionTrackerPort).recordSyncSuccess(
                    eq("track-sync-123"),
                    eq(SyncDirection.LOCAL_TO_REMOTE),
                    any(LocalFilePath.class),
                    eq(RemoteFilePath.of("/remote/backup")),
                    eq(5),
                    anyInt()
            );
        } finally {
            Files.deleteIfExists(tempDir);
        }
    }

    /**
     * Feature: 目錄同步業務流程
     * Scenario: 同步過程輸出埠拋出異常時應記錄失敗並轉換為領域例外
     * Given: 輸出埠拋出未預期異常
     * When: 執行 SyncDirectoryUseCase 的 syncDirectory 方法
     * Then: 拋出 FileTransmissionException，並記錄 Tracker 失敗
     */
    @Test
    @DisplayName("情境：目錄同步失敗時正確拋出 FileTransmissionException")
    void testSyncDirectoryFailure() {
        TransmissionServerConfig serverConfig = TransmissionServerConfig.of(
                "ftps.example.com",
                990,
                TransmissionProtocol.FTPS,
                "ftpsuser",
                "ftpspass"
        );

        SyncDirectoryCommand command = new SyncDirectoryCommand(
                serverConfig,
                LocalFilePath.of("target/dummy-dir"),
                RemoteFilePath.of("/remote/backup"),
                SyncDirection.REMOTE_TO_LOCAL,
                false
        );

        when(fileTransmissionTrackerPort.startTracking(any())).thenReturn("track-sync-err");

        doThrow(new RuntimeException("連線中斷"))
                .when(fileTransmissionGatewayPort)
                .syncDirectory(any(), any(), any(), any(), anyBoolean());

        assertThrows(FileTransmissionException.class, () -> commandService.syncDirectory(command));

        verify(fileTransmissionTrackerPort).recordFailure(eq("track-sync-err"), any());
    }
}
