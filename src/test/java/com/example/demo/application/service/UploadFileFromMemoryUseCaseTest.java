package com.example.demo.application.service;

import com.example.demo.application.port.out.CredentialCipherPort;
import com.example.demo.application.port.out.FileTransmissionGatewayPort;
import com.example.demo.application.port.out.FileTransmissionTrackerPort;
import com.example.demo.application.shared.command.UploadFileFromMemoryCommand;
import com.example.demo.application.shared.dto.FileTransmissionGottenResult;
import com.example.demo.application.shared.exception.FileTransmissionException;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.example.demo.infrastructure.adapter.CredentialCipherAdapter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 記憶體檔案直傳使用案例測試 (UploadFileFromMemoryUseCaseTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 涵蓋 UseCase 正常業務流程、錯誤異常分支與憑證解密驗證；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UploadFileFromMemoryUseCaseTest {

    @Mock
    private FileTransmissionGatewayPort fileTransmissionGatewayPort;

    @Mock
    private FileTransmissionTrackerPort fileTransmissionTrackerPort;

    private FileTransmissionCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new FileTransmissionCommandService(
                fileTransmissionGatewayPort,
                fileTransmissionTrackerPort,
                Runnable::run
        );
    }

    /**
     * Feature: 記憶體檔案直傳業務流程
     * Scenario: 成功將記憶體位元組資料透過 SFTP 直傳至遠端伺服器 (Zero Disk I/O)
     * Given: 準備有效之連線配置、記憶體 byte[] 陣列與遠端目錄路徑
     * When: 執行 UploadFileFromMemoryUseCase 的 uploadFileFromMemory 方法
     * Then: Outbound Port uploadFromMemory 成功被呼叫、回傳狀態為 COMPLETED 之結果 DTO
     */
    @Test
    @DisplayName("情境：成功直傳記憶體檔案資料至遠端目的地")
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

        doNothing().when(fileTransmissionGatewayPort).uploadFromMemory(
                eq(serverConfig),
                eq(content),
                eq(RemoteFilePath.of("/remote/data")),
                eq("memory-file.txt")
        );

        FileTransmissionGottenResult mockResult = new FileTransmissionGottenResult(
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

        FileTransmissionGottenResult result = commandService.uploadFileFromMemory(command);

        assertNotNull(result);
        assertEquals("COMPLETED", result.status());
        assertEquals("SFTP", result.protocol());
        assertEquals("memory-file.txt", result.fileName());
        assertEquals("<memory>", result.sourcePath());
        assertEquals(content.length, result.fileSizeBytes());

        verify(fileTransmissionTrackerPort).startTracking(eq(serverConfig));
        verify(fileTransmissionGatewayPort).uploadFromMemory(
                eq(serverConfig),
                eq(content),
                eq(RemoteFilePath.of("/remote/data")),
                eq("memory-file.txt")
        );
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
     * Scenario: 遠端傳輸閘道發生異常時應中斷流程、記錄 Tracker 失敗並拋出領域例外
     * Given: Outbound Port uploadFromMemory 拋出連線失敗例外
     * When: 執行 UploadFileFromMemoryUseCase 的 uploadFileFromMemory 方法
     * Then: 捕獲 FileTransmissionException 且驗證 Tracker 記錄失敗訊息
     */
    @Test
    @DisplayName("情境：傳輸閘道異常時記錄失敗並拋出例外")
    void testUploadFileFromMemoryFailure() {
        byte[] content = "Failure Test Content".getBytes(StandardCharsets.UTF_8);

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
                "fail-file.txt"
        );

        when(fileTransmissionTrackerPort.startTracking(eq(serverConfig)))
                .thenReturn("track-mem-fail-999");

        doThrow(new RuntimeException("Connection timed out"))
                .when(fileTransmissionGatewayPort).uploadFromMemory(
                        eq(serverConfig),
                        eq(content),
                        eq(RemoteFilePath.of("/remote/data")),
                        eq("fail-file.txt")
                );

        FileTransmissionException ex = assertThrows(
                FileTransmissionException.class,
                () -> commandService.uploadFileFromMemory(command)
        );

        assertTrue(ex.getMessage().contains("Connection timed out"));
        verify(fileTransmissionTrackerPort).recordFailure(eq("track-mem-fail-999"), any());
    }

    /**
     * Feature: 記憶體檔案直傳憑證安全解密
     * Scenario: 命令攜帶 ENC(...) 密文密碼時透過 CredentialCipherPort 解密後執行傳輸
     * Given: 包含 ENC(...) 密文之 TransmissionServerConfig
     * When: 執行 uploadFileFromMemory
     * Then: 傳遞給 Gateway 之配置已被解密為明文
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
                realCipher
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
        FileTransmissionGottenResult mockResult = new FileTransmissionGottenResult(
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

        FileTransmissionGottenResult result = serviceWithCipher.uploadFileFromMemory(command);

        assertNotNull(result);
        assertEquals("COMPLETED", result.status());

        // 驗證傳給 Gateway 的密碼已被解密為明文
        verify(fileTransmissionGatewayPort).uploadFromMemory(
                org.mockito.ArgumentMatchers.argThat(cfg -> "PlainSecretPassword".equals(cfg.password())),
                eq(content),
                eq(RemoteFilePath.of("/secure")),
                eq("secret.txt")
        );
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
        // 模擬遠端目標檔案已存在
        when(fileTransmissionGatewayPort.existsFile(eq(serverConfig), eq(expectedTargetFilePath)))
                .thenReturn(true);

        FileTransmissionGottenResult mockResult = new FileTransmissionGottenResult(
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

        FileTransmissionGottenResult result = commandService.uploadFileFromMemory(command);

        assertNotNull(result);
        assertEquals("COMPLETED", result.status());

        // 驗證檢查檔案存在
        verify(fileTransmissionGatewayPort).existsFile(eq(serverConfig), eq(expectedTargetFilePath));

        // 驗證呼叫 moveFile 將原檔案改名為備份路徑 (.bak)
        org.mockito.ArgumentCaptor<RemoteFilePath> backupPathCaptor = org.mockito.ArgumentCaptor.forClass(RemoteFilePath.class);
        verify(fileTransmissionGatewayPort).moveFile(
                eq(serverConfig),
                eq(expectedTargetFilePath),
                backupPathCaptor.capture()
        );
        assertTrue(backupPathCaptor.getValue().getValue().startsWith("/remote/data/archive-mem_"));
        assertTrue(backupPathCaptor.getValue().getValue().endsWith(".bak"));

        // 驗證呼叫 uploadFromMemory 直傳新版本
        verify(fileTransmissionGatewayPort).uploadFromMemory(
                eq(serverConfig),
                eq(content),
                eq(RemoteFilePath.of("/remote/data")),
                eq("archive-mem.txt")
        );
    }
}
