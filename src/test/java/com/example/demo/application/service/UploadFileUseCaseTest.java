package com.example.demo.application.service;

import com.example.demo.application.port.out.CredentialCipherPort;
import com.example.demo.application.port.out.FileTransmissionGatewayPort;
import com.example.demo.application.port.out.FileTransmissionTrackerPort;
import com.example.demo.application.port.out.TransmissionCompensationStoragePort;
import com.example.demo.application.shared.command.UploadFileCommand;
import com.example.demo.application.shared.dto.FileUploadGottenResult;
import com.example.demo.application.shared.exception.FileTransmissionException;
import com.example.demo.application.shared.vo.CompensationAction;
import com.example.demo.application.shared.vo.CompensationTask;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.example.demo.infrastructure.adapter.CredentialCipherAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.mockito.ArgumentMatchers.anyString;
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
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)；
 * 3. 包含 BL-002 兩階段提交模擬 (2PC Staging & Atomic Commit) 與 Saga 補償機制驗證。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UploadFileUseCaseTest {

    @Mock
    private FileTransmissionGatewayPort fileTransmissionGatewayPort;

    @Mock
    private FileTransmissionTrackerPort fileTransmissionTrackerPort;

    @Mock
    private TransmissionCompensationStoragePort compensationStoragePort;

    private FileTransmissionCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new FileTransmissionCommandService(
                fileTransmissionGatewayPort,
                fileTransmissionTrackerPort,
                Runnable::run,
                CredentialCipherPort.NOOP,
                compensationStoragePort
        );
    }

    /**
     * Feature: 檔案上傳業務流程 (兩階段提交模擬)
     * Scenario: 成功將本機有效檔案透過 SFTP 經由 .staging 暫存檔、校驗與原子更名上傳至遠端
     * Given: 準備有效之連線配置、本機暫存檔案與目標遠端路徑
     * When: 執行 UploadFileUseCase 的 uploadFile 方法
     * Then: 串流寫入 .staging、驗證大小、發送 moveFile 原子更名並回傳 COMPLETED 結果
     */
    @Test
    @DisplayName("情境：成功上傳本機檔案至遠端主機 (2PC Staging & Atomic Commit)")
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

            String expectedStagingName = ".uploaded-file.txt.staging.track-upload-123";
            RemoteFilePath expectedStagingPath = RemoteFilePath.of("/remote/data/" + expectedStagingName);
            RemoteFilePath expectedFinalPath = RemoteFilePath.of("/remote/data/uploaded-file.txt");

            doNothing().when(fileTransmissionGatewayPort).upload(
                    eq(serverConfig),
                    any(LocalFilePath.class),
                    eq(RemoteFilePath.of("/remote/data")),
                    eq(expectedStagingName)
            );

            when(fileTransmissionGatewayPort.existsFile(eq(serverConfig), eq(expectedStagingPath)))
                    .thenReturn(true);
            when(fileTransmissionGatewayPort.getRemoteFileSize(eq(serverConfig), eq(expectedStagingPath)))
                    .thenReturn(Files.size(tempFile));

            FileUploadGottenResult mockResult = new FileUploadGottenResult(
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

            FileUploadGottenResult result = commandService.uploadFile(command);

            assertNotNull(result);
            assertEquals("COMPLETED", result.status());
            assertEquals("SFTP", result.protocol());
            assertEquals("uploaded-file.txt", result.fileName());
            assertTrue(result.fileSizeBytes() > 0);

            // 驗證 2PC 階段追蹤調用
            verify(fileTransmissionTrackerPort).recordStaging(eq("track-upload-123"), eq(expectedStagingPath.getValue()));
            verify(fileTransmissionTrackerPort).recordVerifying(eq("track-upload-123"));
            verify(fileTransmissionTrackerPort).recordCommitting(eq("track-upload-123"));

            // 驗證原子更名提交
            verify(fileTransmissionGatewayPort).moveFile(eq(serverConfig), eq(expectedStagingPath), eq(expectedFinalPath));
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
                cipherAdapter,
                compensationStoragePort
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
            when(fileTransmissionGatewayPort.existsFile(any(), any())).thenReturn(true);
            when(fileTransmissionGatewayPort.getRemoteFileSize(any(), any())).thenReturn(Files.size(tempFile));

            when(fileTransmissionTrackerPort.recordUploadSuccess(any(), any(), any(), any(), anyLong()))
                    .thenReturn(new FileUploadGottenResult(
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

            FileUploadGottenResult result = serviceWithCipher.uploadFile(command);

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
            String stagingName = ".report.txt.staging.track-upload-archive";
            RemoteFilePath expectedStagingFilePath = RemoteFilePath.of("/remote/data/" + stagingName);

            // 模擬遠端 staging 檔案與既有正式檔案皆存在
            when(fileTransmissionGatewayPort.existsFile(eq(serverConfig), eq(expectedStagingFilePath)))
                    .thenReturn(true);
            when(fileTransmissionGatewayPort.existsFile(eq(serverConfig), eq(expectedTargetFilePath)))
                    .thenReturn(true);
            when(fileTransmissionGatewayPort.getRemoteFileSize(eq(serverConfig), eq(expectedStagingFilePath)))
                    .thenReturn(Files.size(tempFile));

            FileUploadGottenResult mockResult = new FileUploadGottenResult(
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

            FileUploadGottenResult result = commandService.uploadFile(command);

            assertNotNull(result);
            assertEquals("COMPLETED", result.status());

            // 驗證呼叫 moveFile 將原檔案改名為備份路徑 (.bak)
            ArgumentCaptor<RemoteFilePath> backupPathCaptor = ArgumentCaptor.forClass(RemoteFilePath.class);
            verify(fileTransmissionGatewayPort).moveFile(
                    eq(serverConfig),
                    eq(expectedTargetFilePath),
                    backupPathCaptor.capture()
            );
            assertTrue(backupPathCaptor.getValue().getValue().startsWith("/remote/data/report_"));
            assertTrue(backupPathCaptor.getValue().getValue().endsWith(".bak"));

            // 驗證呼叫 moveFile 將暫存檔切換為正式檔名
            verify(fileTransmissionGatewayPort).moveFile(
                    eq(serverConfig),
                    eq(expectedStagingFilePath),
                    eq(expectedTargetFilePath)
            );
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Feature: 2PC 暫存大小校驗異常觸發補償
     * Scenario: 遠端暫存檔上傳後大小與來源檔案不相符
     * Given: 遠端回傳之暫存檔大小小於本機檔案大小
     * When: 執行 uploadFile 方法
     * Then: 校驗失敗拋出例外，發起即時刪除清理暫存檔
     */
    @Test
    @DisplayName("情境：遠端暫存檔大小校驗失敗時，觸發即時補償刪除並拋出例外")
    void testUploadFileSizeMismatchTriggersCompensation() throws IOException {
        Path tempFile = Files.createTempFile("test-size-mismatch-", ".txt");
        Files.writeString(tempFile, "Complete File Content");

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
                    "mismatch.txt"
            );

            when(fileTransmissionTrackerPort.startTracking(eq(serverConfig)))
                    .thenReturn("track-upload-mismatch");

            String stagingName = ".mismatch.txt.staging.track-upload-mismatch";
            RemoteFilePath stagingPath = RemoteFilePath.of("/remote/data/" + stagingName);

            when(fileTransmissionGatewayPort.existsFile(eq(serverConfig), eq(stagingPath))).thenReturn(true);
            // 模擬遠端大小僅為 5 bytes (小於實際長度)
            when(fileTransmissionGatewayPort.getRemoteFileSize(eq(serverConfig), eq(stagingPath))).thenReturn(5L);
            when(fileTransmissionGatewayPort.deleteFile(eq(serverConfig), eq(stagingPath))).thenReturn(true);

            assertThrows(FileTransmissionException.class, () -> commandService.uploadFile(command));

            // 驗證觸發即時刪除暫存檔
            verify(fileTransmissionGatewayPort).deleteFile(eq(serverConfig), eq(stagingPath));
            verify(fileTransmissionTrackerPort).recordFailure(eq("track-upload-mismatch"), anyString());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Feature: 即時補償失敗時寫入 Saga 補償 Outbox 表
     * Scenario: 遠端暫存檔校驗失敗且即時刪除失敗 (例如網路斷線)
     * Given: 即時 deleteFile 拋出異常或回傳 false
     * When: 執行 uploadFile 拋出異常
     * Then: 呼叫 TransmissionCompensationStoragePort 將任務持久化至 Outbox
     */
    @Test
    @DisplayName("情境：即時清理暫存檔失敗時，將補償任務寫入 Outbox 表")
    void testImmediateCompensationFailureWritesToOutbox() throws IOException {
        Path tempFile = Files.createTempFile("test-outbox-", ".txt");
        Files.writeString(tempFile, "File Content For Outbox Test");

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
                    "outbox.txt"
            );

            when(fileTransmissionTrackerPort.startTracking(eq(serverConfig)))
                    .thenReturn("track-upload-outbox");

            String stagingName = ".outbox.txt.staging.track-upload-outbox";
            RemoteFilePath stagingPath = RemoteFilePath.of("/remote/data/" + stagingName);

            doThrow(new RuntimeException("網路中途斷線"))
                    .when(fileTransmissionGatewayPort)
                    .upload(any(), any(), any(), any());

            // 模擬即時刪除亦遭遇遠端連線中斷
            when(fileTransmissionGatewayPort.existsFile(eq(serverConfig), eq(stagingPath))).thenReturn(true);
            when(fileTransmissionGatewayPort.deleteFile(eq(serverConfig), eq(stagingPath))).thenReturn(false);

            assertThrows(FileTransmissionException.class, () -> commandService.uploadFile(command));

            // 驗證寫入 Outbox 補償日誌
            ArgumentCaptor<CompensationTask> taskCaptor = ArgumentCaptor.forClass(CompensationTask.class);
            verify(compensationStoragePort).saveCompensation(taskCaptor.capture());

            CompensationTask savedTask = taskCaptor.getValue();
            assertEquals("track-upload-outbox", savedTask.trackingId());
            assertEquals(CompensationAction.DELETE_REMOTE_STAGING, savedTask.actionType());
            assertEquals(stagingPath.getValue(), savedTask.remotePath());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
