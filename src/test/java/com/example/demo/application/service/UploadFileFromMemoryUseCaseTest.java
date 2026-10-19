package com.example.demo.application.service;

import com.example.demo.application.port.out.CredentialCipherPort;
import com.example.demo.application.port.out.FileTransmissionGatewayPort;
import com.example.demo.application.port.out.FileTransmissionTrackerPort;
import com.example.demo.application.port.out.TransmissionCompensationStoragePort;
import com.example.demo.application.shared.command.UploadFileFromMemoryCommand;
import com.example.demo.application.shared.dto.FileUploadGottenResult;
import com.example.demo.application.shared.exception.FileTransmissionException;
import com.example.demo.application.shared.vo.CompensationAction;
import com.example.demo.application.shared.vo.CompensationTask;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.example.demo.infrastructure.adapter.CredentialCipherAdapter;
import java.nio.charset.StandardCharsets;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 記憶體檔案直傳使用案例測試 (UploadFileFromMemoryUseCaseTest)
 * <p>
 * 遵循測試規範：
 * 1. 涵蓋 UseCase 正常業務流程與錯誤異常分支驗證；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)；
 * 3. 驗證 BL-002 零磁碟落盤之 2PC 暫存與原子提交機制。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UploadFileFromMemoryUseCaseTest {

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
     * Feature: 記憶體檔案直傳業務流程 (兩階段提交模擬)
     * Scenario: 成功將記憶體 byte[] 串流透過 SFTP 經由 .staging 暫存檔、校驗與原子更名直傳至遠端
     * Given: 準備有效之連線配置、記憶體 byte[] 與目標遠端路徑
     * When: 執行 UploadFileFromMemoryUseCase 的 uploadFileFromMemory 方法
     * Then: 串流寫入 .staging、校驗大小、發送 moveFile 原子更名並回傳 COMPLETED 結果
     */
    @Test
    @DisplayName("情境：成功直傳記憶體檔案資料至遠端目的地 (2PC Staging & Atomic Commit)")
    void testUploadFileFromMemorySuccess() {
        byte[] content = "Direct Memory Upload Content".getBytes(StandardCharsets.UTF_8);

        TransmissionServerConfig serverConfig = TransmissionServerConfig.of(
                "192.168.1.100",
                22,
                TransmissionProtocol.SFTP,
                "testuser",
                "testpass"
        );

        UploadFileFromMemoryCommand command = new UploadFileFromMemoryCommand(
                serverConfig,
                content,
                RemoteFilePath.of("/remote/data"),
                "memory-file.txt"
        );

        when(fileTransmissionTrackerPort.startTracking(eq(serverConfig)))
                .thenReturn("track-mem-123");

        String stagingName = ".memory-file.txt.staging.track-mem-123";
        RemoteFilePath stagingPath = RemoteFilePath.of("/remote/data/" + stagingName);
        RemoteFilePath targetPath = RemoteFilePath.of("/remote/data/memory-file.txt");

        doNothing().when(fileTransmissionGatewayPort).uploadFromMemory(
                eq(serverConfig),
                eq(content),
                eq(RemoteFilePath.of("/remote/data")),
                eq(stagingName)
        );

        when(fileTransmissionGatewayPort.existsFile(eq(serverConfig), eq(stagingPath))).thenReturn(true);
        when(fileTransmissionGatewayPort.getRemoteFileSize(eq(serverConfig), eq(stagingPath))).thenReturn((long) content.length);

        FileUploadGottenResult mockResult = new FileUploadGottenResult(
                "track-mem-123",
                "SFTP",
                "COMPLETED",
                "<memory>",
                "/remote/data",
                "memory-file.txt",
                content.length,
                30L,
                null
        );

        when(fileTransmissionTrackerPort.recordUploadSuccess(
                eq("track-mem-123"),
                isNull(),
                eq(RemoteFilePath.of("/remote/data")),
                eq("memory-file.txt"),
                eq((long) content.length)
        )).thenReturn(mockResult);

        FileUploadGottenResult result = commandService.uploadFileFromMemory(command);

        assertNotNull(result);
        assertEquals("COMPLETED", result.status());
        assertEquals("SFTP", result.protocol());
        assertEquals("memory-file.txt", result.fileName());
        assertEquals("<memory>", result.sourcePath());
        assertEquals(content.length, result.fileSizeBytes());

        // 驗證 2PC 階段追蹤調用
        verify(fileTransmissionTrackerPort).recordStaging(eq("track-mem-123"), eq(stagingPath.getValue()));
        verify(fileTransmissionTrackerPort).recordVerifying(eq("track-mem-123"));
        verify(fileTransmissionTrackerPort).recordCommitting(eq("track-mem-123"));

        // 驗證原子更名與成功審計
        verify(fileTransmissionGatewayPort).moveFile(eq(serverConfig), eq(stagingPath), eq(targetPath));
        verify(fileTransmissionTrackerPort).recordUploadSuccess(
                eq("track-mem-123"),
                isNull(),
                eq(RemoteFilePath.of("/remote/data")),
                eq("memory-file.txt"),
                eq((long) content.length)
        );
    }

    /**
     * Feature: 記憶體檔案直傳業務流程
     * Scenario: 遠端通訊發生異常時拋出 FileTransmissionException 並記錄追蹤失敗
     * Given: 傳輸閘道拋出底層通訊異常
     * When: 執行 uploadFileFromMemory 方法
     * Then: 捕獲並包裝為 FileTransmissionException，並通知 Tracker 記錄失敗
     */
    @Test
    @DisplayName("情境：傳輸閘道發生異常時拋出 FileTransmissionException")
    void testUploadFileFromMemoryGatewayThrowsException() {
        byte[] content = "Error Content".getBytes(StandardCharsets.UTF_8);

        TransmissionServerConfig serverConfig = TransmissionServerConfig.of(
                "192.168.1.100",
                21,
                TransmissionProtocol.FTP,
                "testuser",
                "testpass"
        );

        UploadFileFromMemoryCommand command = new UploadFileFromMemoryCommand(
                serverConfig,
                content,
                RemoteFilePath.of("/remote/data"),
                "fail-mem.txt"
        );

        when(fileTransmissionTrackerPort.startTracking(any())).thenReturn("track-mem-fail");

        doThrow(new RuntimeException("連線失敗"))
                .when(fileTransmissionGatewayPort)
                .uploadFromMemory(any(), any(), any(), any());

        assertThrows(FileTransmissionException.class, () -> commandService.uploadFileFromMemory(command));

        verify(fileTransmissionTrackerPort).recordFailure(eq("track-mem-fail"), any());
    }

    /**
     * Feature: 記憶體檔案直傳機密憑證自動解密
     * Scenario: 當命令配置帶有 ENC(...) 密文時，自動解密為明文傳遞至 Gateway
     * Given: 包含 ENC(...) 密文密碼之配置
     * When: 執行 uploadFileFromMemory
     * Then: Gateway 收到之配置包含解密後的明文密碼
     */
    @Test
    @DisplayName("情境：上傳時自動解密 ENC(...) 密文憑證")
    void testUploadFileFromMemoryWithEncryptedPassword() {
        CredentialCipherPort realCipher = new CredentialCipherAdapter("DefaultTestSecretKey1234567890!!");
        String encryptedPass = realCipher.encrypt("PlainSecretPassword");

        FileTransmissionCommandService serviceWithCipher = new FileTransmissionCommandService(
                fileTransmissionGatewayPort,
                fileTransmissionTrackerPort,
                Runnable::run,
                realCipher,
                compensationStoragePort
        );

        byte[] content = "Secret Content".getBytes(StandardCharsets.UTF_8);
        TransmissionServerConfig encryptedConfig = TransmissionServerConfig.of(
                "sftp.example.com",
                22,
                TransmissionProtocol.SFTP,
                "cipher_user",
                encryptedPass
        );

        UploadFileFromMemoryCommand command = new UploadFileFromMemoryCommand(
                encryptedConfig,
                content,
                RemoteFilePath.of("/secure"),
                "secret.txt"
        );

        when(fileTransmissionTrackerPort.startTracking(any())).thenReturn("track-cipher-1");
        when(fileTransmissionGatewayPort.existsFile(any(), any())).thenReturn(true);
        when(fileTransmissionGatewayPort.getRemoteFileSize(any(), any())).thenReturn((long) content.length);

        FileUploadGottenResult mockResult = new FileUploadGottenResult(
                "track-cipher-1",
                "SFTP",
                "COMPLETED",
                "<memory>",
                "/secure",
                "secret.txt",
                content.length,
                20L,
                null
        );
        when(fileTransmissionTrackerPort.recordUploadSuccess(any(), isNull(), any(), any(), anyLong()))
                .thenReturn(mockResult);

        FileUploadGottenResult result = serviceWithCipher.uploadFileFromMemory(command);

        assertNotNull(result);
        assertEquals("COMPLETED", result.status());

        // 驗證傳給 Gateway 的密碼已被解密為明文
        ArgumentCaptor<TransmissionServerConfig> cfgCaptor = ArgumentCaptor.forClass(TransmissionServerConfig.class);
        verify(fileTransmissionGatewayPort).uploadFromMemory(
                cfgCaptor.capture(),
                eq(content),
                eq(RemoteFilePath.of("/secure")),
                anyString()
        );
        assertEquals("PlainSecretPassword", cfgCaptor.getValue().password());
    }

    /**
     * Feature: 記憶體檔案直傳業務流程 (方案 A 歷史歸檔策略)
     * Scenario: 當遠端伺服器已存在同名檔案時，先自動備份舊檔案為 .bak 後再執行記憶體直傳
     * Given: 準備記憶體 byte[] 陣列，模擬遠端已存在同名檔案 (existsFile 回傳 true)
     * When: 執行 UploadFileFromMemoryUseCase 的 uploadFileFromMemory 方法
     * Then: 驗證先呼叫 moveFile 將既有檔案備份為 .bak 格式，再呼叫 uploadFromMemory 直傳新版本
     */
    @Test
    @DisplayName("情境：記憶體直傳時若遠端檔案已存在，自動歸檔既有檔案為 .bak 後上傳最新版 (方案 A)")
    void testUploadFileFromMemoryWhenRemoteFileExistsArchivedBeforeUpload() {
        byte[] content = "Memory New Content".getBytes(StandardCharsets.UTF_8);

        TransmissionServerConfig serverConfig = TransmissionServerConfig.of(
                "192.168.1.100",
                22,
                TransmissionProtocol.SFTP,
                "testuser",
                "testpass"
        );

        UploadFileFromMemoryCommand command = new UploadFileFromMemoryCommand(
                serverConfig,
                content,
                RemoteFilePath.of("/remote/data"),
                "archive-mem.txt"
        );

        when(fileTransmissionTrackerPort.startTracking(eq(serverConfig)))
                .thenReturn("track-mem-archive");

        RemoteFilePath expectedTargetFilePath = RemoteFilePath.of("/remote/data/archive-mem.txt");
        String stagingName = ".archive-mem.txt.staging.track-mem-archive";
        RemoteFilePath stagingPath = RemoteFilePath.of("/remote/data/" + stagingName);

        // 模擬遠端 staging 檔案與既有正式檔案皆存在
        when(fileTransmissionGatewayPort.existsFile(eq(serverConfig), eq(stagingPath))).thenReturn(true);
        when(fileTransmissionGatewayPort.existsFile(eq(serverConfig), eq(expectedTargetFilePath))).thenReturn(true);
        when(fileTransmissionGatewayPort.getRemoteFileSize(eq(serverConfig), eq(stagingPath))).thenReturn((long) content.length);

        FileUploadGottenResult mockResult = new FileUploadGottenResult(
                "track-mem-archive",
                "SFTP",
                "COMPLETED",
                "<memory>",
                "/remote/data",
                "archive-mem.txt",
                content.length,
                30L,
                null
        );

        when(fileTransmissionTrackerPort.recordUploadSuccess(
                eq("track-mem-archive"),
                isNull(),
                eq(RemoteFilePath.of("/remote/data")),
                eq("archive-mem.txt"),
                eq((long) content.length)
        )).thenReturn(mockResult);

        FileUploadGottenResult result = commandService.uploadFileFromMemory(command);

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
        assertTrue(backupPathCaptor.getValue().getValue().startsWith("/remote/data/archive-mem_"));
        assertTrue(backupPathCaptor.getValue().getValue().endsWith(".bak"));

        // 驗證呼叫 moveFile 將暫存檔切換為正式檔名
        verify(fileTransmissionGatewayPort).moveFile(
                eq(serverConfig),
                eq(stagingPath),
                eq(expectedTargetFilePath)
        );
    }
}
