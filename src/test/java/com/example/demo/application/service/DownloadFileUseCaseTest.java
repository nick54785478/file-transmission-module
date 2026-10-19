package com.example.demo.application.service;

import com.example.demo.application.shared.command.DownloadFileCommand;
import com.example.demo.application.shared.dto.FileDownloadGottenResult;
import com.example.demo.application.port.out.FileTransmissionGatewayPort;
import com.example.demo.application.port.out.FileTransmissionTrackerPort;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFilePath;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 檔案下載使用案例測試 (DownloadFileUseCaseTest)
 * <p>
 * 遵循測試規範：
 * 1. 涵蓋 UseCase 正常業務流程與錯誤異常分支驗證；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class DownloadFileUseCaseTest {

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
     * Feature: 檔案下載業務流程
     * Scenario: 成功從遠端 FTP 伺服器下載檔案至本機目標路徑
     * Given: 準備有效之連線配置、遠端檔案路徑與本機目標儲存路徑
     * When: 執行 DownloadFileUseCase 的 downloadFile 方法
     * Then: Outbound Port 成功被呼叫、回傳狀態為 COMPLETED 之結果 DTO
     */
    @Test
    @DisplayName("情境：成功自遠端下載檔案至本機")
    void testDownloadFileSuccess() throws IOException {
        Path tempFile = Files.createTempFile("test-download-", ".txt");

        try {
            TransmissionServerConfig serverConfig = TransmissionServerConfig.of(
                    "ftp.example.com",
                    21,
                    TransmissionProtocol.FTP,
                    "ftpuser",
                    "ftppass"
            );

            DownloadFileCommand command = new DownloadFileCommand(
                    serverConfig,
                    RemoteFilePath.of("/remote/folder/report.pdf"),
                    LocalFilePath.of(tempFile.toString())
            );

            when(fileTransmissionTrackerPort.startTracking(eq(serverConfig)))
                    .thenReturn("track-download-123");

            doAnswer(invocation -> {
                Files.writeString(tempFile, "Mocked downloaded content");
                return null;
            }).when(fileTransmissionGatewayPort).download(
                    eq(serverConfig),
                    eq(RemoteFilePath.of("/remote/folder/report.pdf")),
                    any(LocalFilePath.class)
            );

            FileDownloadGottenResult mockResult = new FileDownloadGottenResult(
                    "track-download-123",
                    "FTP",
                    "COMPLETED",
                    "/remote/folder/report.pdf",
                    tempFile.toString(),
                    "report.pdf",
                    Files.size(tempFile),
                    80L,
                    null
            );

            when(fileTransmissionTrackerPort.recordDownloadSuccess(
                    eq("track-download-123"),
                    eq(RemoteFilePath.of("/remote/folder/report.pdf")),
                    any(LocalFilePath.class),
                    eq("report.pdf"),
                    anyLong()
            )).thenReturn(mockResult);

            FileDownloadGottenResult result = commandService.downloadFile(command);

            assertNotNull(result);
            assertEquals("COMPLETED", result.status());
            assertEquals("FTP", result.protocol());
            assertEquals("report.pdf", result.fileName());

            verify(fileTransmissionTrackerPort).startTracking(eq(serverConfig));
            verify(fileTransmissionGatewayPort).download(
                    eq(serverConfig),
                    eq(RemoteFilePath.of("/remote/folder/report.pdf")),
                    any(LocalFilePath.class)
            );
            verify(fileTransmissionTrackerPort).recordDownloadSuccess(
                    eq("track-download-123"),
                    eq(RemoteFilePath.of("/remote/folder/report.pdf")),
                    any(LocalFilePath.class),
                    eq("report.pdf"),
                    anyLong()
            );
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Feature: 檔案下載業務流程
     * Scenario: 遠端下載失敗時應正確記錄實體狀態並拋出領域例外
     * Given: 遠端主機檔案不存在或連線中斷
     * When: 執行 DownloadFileUseCase 的 downloadFile 方法
     * Then: 拋出 FileTransmissionException，並記錄 Tracker 失敗
     */
    @Test
    @DisplayName("情境：遠端檔案下載失敗時拋出 FileTransmissionException")
    void testDownloadFileFailure() {
        TransmissionServerConfig serverConfig = TransmissionServerConfig.of(
                "ftp.example.com",
                21,
                TransmissionProtocol.FTP,
                "ftpuser",
                "ftppass"
        );

        DownloadFileCommand command = new DownloadFileCommand(
                serverConfig,
                RemoteFilePath.of("/remote/folder/not-found.pdf"),
                LocalFilePath.of("target/dummy.txt")
        );

        when(fileTransmissionTrackerPort.startTracking(any())).thenReturn("track-download-err");

        doThrow(new RuntimeException("遠端檔案不存在"))
                .when(fileTransmissionGatewayPort)
                .download(any(), any(), any());

        assertThrows(FileTransmissionException.class, () -> commandService.downloadFile(command));

        verify(fileTransmissionTrackerPort).recordFailure(eq("track-download-err"), any());
    }
}
