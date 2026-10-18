package com.example.demo.application.service;

import com.example.demo.application.port.out.CredentialCipherPort;
import com.example.demo.application.port.out.FileTransmissionGatewayPort;
import com.example.demo.application.port.out.FileTransmissionTrackerPort;
import com.example.demo.application.shared.command.UploadFileCommand;
import com.example.demo.application.shared.dto.FileTransmissionGottenResult;
import com.example.demo.application.shared.exception.FileTransmissionException;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.example.demo.infrastructure.adapter.CredentialCipherAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 檔案上傳使用案例測試 (UploadFileUseCaseTest)
 * <p>
 * 遵循測試規範：
 * 1. 涵蓋 UseCase 正常業務流程與錯誤異常分支驗證；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UploadFileUseCaseTest {

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
     * Feature: 檔案上傳業務流程
     * Scenario: 成功將本機有效檔案透過 SFTP 上傳至遠端伺服器
     * Given: 準備有效之連線配置、本機暫存檔案與目標遠端路徑
     * When: 執行 UploadFileUseCase 的 uploadFile 方法
     * Then: Outbound Port 成功被呼叫、回傳狀態為 COMPLETED 之結果 DTO
     */
    @Test
    @DisplayName("情境：成功上傳本機檔案至遠端主機")
    void testUploadFileSuccess() throws IOException {
        Path tempFile = Files.createTempFile("test-upload-", ".txt");
        Files.writeString(tempFile, "Hello SFTP Upload Test");

        try {
            TransmissionServerConfig serverConfig = TransmissionServerConfig.of(
                    "192.168.1.100",
                    22,
                    TransmissionProtocol.SFTP,
                    "testuser",
                    "testpass"
            );

            UploadFileCommand command = new UploadFileCommand(
                    serverConfig,
                    LocalFilePath.of(tempFile.toString()),
                    RemoteFilePath.of("/remote/data"),
                    "uploaded-file.txt"
            );

            when(fileTransmissionTrackerPort.startTracking(eq(serverConfig)))
                    .thenReturn("track-upload-123");

            doNothing().when(fileTransmissionGatewayPort).upload(
                    eq(serverConfig),
                    any(LocalFilePath.class),
                    eq(RemoteFilePath.of("/remote/data")),
                    eq("uploaded-file.txt")
            );

            FileTransmissionGottenResult mockResult = new FileTransmissionGottenResult(
                    "track-upload-123",
                    "SFTP",
                    "COMPLETED",
                    tempFile.toString(),
                    "/remote/data",
                    "uploaded-file.txt",
                    Files.size(tempFile),
                    50L,
                    null
            );

            when(fileTransmissionTrackerPort.recordUploadSuccess(
                    eq("track-upload-123"),
                    any(LocalFilePath.class),
                    eq(RemoteFilePath.of("/remote/data")),
                    eq("uploaded-file.txt"),
                    anyLong()
            )).thenReturn(mockResult);

            FileTransmissionGottenResult result = commandService.uploadFile(command);

            assertNotNull(result);
            assertEquals("COMPLETED", result.status());
            assertEquals("SFTP", result.protocol());
            assertEquals("uploaded-file.txt", result.fileName());
            assertTrue(result.fileSizeBytes() > 0);

            verify(fileTransmissionTrackerPort).startTracking(eq(serverConfig));
            verify(fileTransmissionGatewayPort).upload(
                    eq(serverConfig),
                    any(LocalFilePath.class),
                    eq(RemoteFilePath.of("/remote/data")),
                    eq("uploaded-file.txt")
            );
            verify(fileTransmissionTrackerPort).recordUploadSuccess(
                    eq("track-upload-123"),
                    any(LocalFilePath.class),
                    eq(RemoteFilePath.of("/remote/data")),
                    eq("uploaded-file.txt"),
                    anyLong()
            );
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Feature: 檔案上傳業務流程
     * Scenario: 本機檔案不存在時應中斷流程並拋出領域例外
     * Given: 指定一不存在之本機檔案路徑
     * When: 執行 UploadFileUseCase 的 uploadFile 方法
     * Then: 拋出 FileTransmissionException 且不呼叫傳輸輸出埠，並記錄 Tracker 失敗
     */
    @Test
    @DisplayName("情境：本機檔案不存在時拋出 FileTransmissionException")
    void testUploadFileLocalFileNotFound() {
        TransmissionServerConfig serverConfig = TransmissionServerConfig.of(
                "192.168.1.100",
                22,
                TransmissionProtocol.SFTP,
                "testuser",
                "testpass"
        );

        UploadFileCommand command = new UploadFileCommand(
                serverConfig,
                LocalFilePath.of("Z:\\non_existent_path\\ghost.txt"),
                RemoteFilePath.of("/remote/data"),
                "ghost.txt"
        );

        when(fileTransmissionTrackerPort.startTracking(any())).thenReturn("track-upload-fail");

        assertThrows(FileTransmissionException.class, () -> commandService.uploadFile(command));

        verify(fileTransmissionTrackerPort).recordFailure(eq("track-upload-fail"), any());
    }

    /**
     * Feature: 檔案上傳業務流程
     * Scenario: 傳輸輸出埠發生異常時應正確包裝並拋出 FileTransmissionException
     * Given: 模擬傳輸輸出埠拋出底層通訊異常
     * When: 執行 UploadFileUseCase 的 uploadFile 方法
     * Then: 捕獲並拋出 FileTransmissionException，並記錄 Tracker 失敗
     */
    @Test
    @DisplayName("情境：傳輸輸出埠發生異常時應正確拋出 FileTransmissionException")
    void testUploadFilePortThrowsException() throws IOException {
        Path tempFile = Files.createTempFile("test-fail-", ".txt");
        Files.writeString(tempFile, "Fail Test");

        try {
            TransmissionServerConfig serverConfig = TransmissionServerConfig.of(
                    "192.168.1.100",
                    21,
                    TransmissionProtocol.FTP,
                    "testuser",
                    "testpass"
            );

            UploadFileCommand command = new UploadFileCommand(
                    serverConfig,
                    LocalFilePath.of(tempFile.toString()),
                    RemoteFilePath.of("/remote/data"),
                    "file.txt"
            );

            when(fileTransmissionTrackerPort.startTracking(any())).thenReturn("track-upload-err");

            doThrow(new RuntimeException("連線逾時"))
                    .when(fileTransmissionGatewayPort)
                    .upload(any(), any(), any(), any());

            assertThrows(FileTransmissionException.class, () -> commandService.uploadFile(command));

            verify(fileTransmissionTrackerPort).recordFailure(eq("track-upload-err"), any());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Feature: 檔案上傳機密憑證自動解密調度
     * Scenario: 當 UploadFileCommand 中的伺服器配置帶有 ENC(...) 加密密碼時，應用服務調用 CredentialCipherPort 解密並將明文送至傳輸閘道
     * Given: 包含 ENC(...) 加密密碼之 Command 與自訂 CredentialCipherPort
     * When: 執行 uploadFile 命令
     * Then: 傳輸閘道收到之 TransmissionServerConfig 密碼為解密後之明文
     */
    @Test
    @DisplayName("情境：上傳服務自動調用 CredentialCipherPort 解密 ENC(...) 密碼後傳予通訊閘道")
    void testUploadFileWithEncryptedPasswordDecryptedByService() throws IOException {
        Path tempFile = Files.createTempFile("test-enc-upload-", ".txt");
        Files.writeString(tempFile, "Encrypted Password Test Content");

        CredentialCipherPort cipherAdapter = new CredentialCipherAdapter("SecretKeyForUploadTest2026");
        String plainPassword = "RawSecretPassword123";
        String encPassword = cipherAdapter.encrypt(plainPassword);

        FileTransmissionCommandService serviceWithCipher = new FileTransmissionCommandService(
                fileTransmissionGatewayPort,
                fileTransmissionTrackerPort,
                Runnable::run,
                cipherAdapter
        );

        try {
            TransmissionServerConfig rawConfig = TransmissionServerConfig.of(
                    "192.168.1.100",
                    22,
                    TransmissionProtocol.SFTP,
                    "sftp_user",
                    encPassword
            );

            UploadFileCommand command = new UploadFileCommand(
                    rawConfig,
                    LocalFilePath.of(tempFile.toString()),
                    RemoteFilePath.of("/remote/data"),
                    "enc_file.txt"
            );

            when(fileTransmissionTrackerPort.startTracking(any())).thenReturn("track-upload-enc");
            when(fileTransmissionTrackerPort.recordUploadSuccess(any(), any(), any(), any(), anyLong()))
                    .thenReturn(new FileTransmissionGottenResult(
                            "track-upload-enc",
                            "SFTP",
                            "COMPLETED",
                            tempFile.toString(),
                            "/remote/data",
                            "enc_file.txt",
                            Files.size(tempFile),
                            100L,
                            null
                    ));

            FileTransmissionGottenResult result = serviceWithCipher.uploadFile(command);

            assertNotNull(result);
            assertEquals("COMPLETED", result.status());

            // 驗證傳給 Gateway 的連線配置已被解密為原始明文
            ArgumentCaptor<TransmissionServerConfig> configCaptor =
                    ArgumentCaptor.forClass(TransmissionServerConfig.class);
            verify(fileTransmissionGatewayPort).upload(configCaptor.capture(), any(), any(), any());
            assertEquals(plainPassword, configCaptor.getValue().getPassword());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Feature: 檔案上傳業務流程 (方案 A 歷史歸檔策略)
     * Scenario: 當遠端伺服器已存在同名檔案時，先自動備份舊檔案為 .bak 後再執行新檔案上傳
     * Given: 準備本機檔案與遠端路徑，模擬遠端已存在同名檔案 (existsFile 回傳 true)
     * When: 執行 UploadFileUseCase 的 uploadFile 方法
     * Then: 驗證先呼叫 moveFile 將既有檔案備份為 .bak 格式，再呼叫 upload 寫入最新版本
     */
    @Test
    @DisplayName("情境：遠端檔案已存在時，自動歸檔既有檔案為 .bak 後上傳最新版 (方案 A)")
    void testUploadFileWhenRemoteFileExistsArchivedBeforeUpload() throws IOException {
        Path tempFile = Files.createTempFile("test-archive-", ".txt");
        Files.writeString(tempFile, "New Version Content");

        try {
            TransmissionServerConfig serverConfig = TransmissionServerConfig.of(
                    "192.168.1.100",
                    22,
                    TransmissionProtocol.SFTP,
                    "testuser",
                    "testpass"
            );

            UploadFileCommand command = new UploadFileCommand(
                    serverConfig,
                    LocalFilePath.of(tempFile.toString()),
                    RemoteFilePath.of("/remote/data"),
                    "report.txt"
            );

            when(fileTransmissionTrackerPort.startTracking(eq(serverConfig)))
                    .thenReturn("track-upload-archive");

            RemoteFilePath expectedTargetFilePath = RemoteFilePath.of("/remote/data/report.txt");
            // 模擬遠端目標檔案已存在
            when(fileTransmissionGatewayPort.existsFile(eq(serverConfig), eq(expectedTargetFilePath)))
                    .thenReturn(true);

            FileTransmissionGottenResult mockResult = new FileTransmissionGottenResult(
                    "track-upload-archive",
                    "SFTP",
                    "COMPLETED",
                    tempFile.toString(),
                    "/remote/data",
                    "report.txt",
                    Files.size(tempFile),
                    50L,
                    null
            );

            when(fileTransmissionTrackerPort.recordUploadSuccess(
                    eq("track-upload-archive"),
                    any(LocalFilePath.class),
                    eq(RemoteFilePath.of("/remote/data")),
                    eq("report.txt"),
                    anyLong()
            )).thenReturn(mockResult);

            FileTransmissionGottenResult result = commandService.uploadFile(command);

            assertNotNull(result);
            assertEquals("COMPLETED", result.status());

            // 驗證檢查檔案存在
            verify(fileTransmissionGatewayPort).existsFile(eq(serverConfig), eq(expectedTargetFilePath));

            // 驗證呼叫 moveFile 將原檔案改名為備份路徑 (.bak)
            ArgumentCaptor<RemoteFilePath> backupPathCaptor = ArgumentCaptor.forClass(RemoteFilePath.class);
            verify(fileTransmissionGatewayPort).moveFile(
                    eq(serverConfig),
                    eq(expectedTargetFilePath),
                    backupPathCaptor.capture()
            );
            assertTrue(backupPathCaptor.getValue().getValue().startsWith("/remote/data/report_"));
            assertTrue(backupPathCaptor.getValue().getValue().endsWith(".bak"));

            // 驗證呼叫 upload 將新檔案寫入原正規檔名
            verify(fileTransmissionGatewayPort).upload(
                    eq(serverConfig),
                    any(LocalFilePath.class),
                    eq(RemoteFilePath.of("/remote/data")),
                    eq("report.txt")
            );
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
