package com.example.demo.infrastructure.transmission.client.impl;

import com.example.demo.application.shared.exception.TransmissionConnectionException;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFileInfo;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FTPS (FTP over SSL/TLS) 安全傳輸協定客戶端單元測試 (FtpsTransmissionClientTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 驗證 {@link FtpsTransmissionClient} 的 TLS 加密協定支援度、記憶體上傳、檔案傳輸回呼與連線異常轉譯機制；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FtpsTransmissionClientTest {

    @Mock
    private FTPSClient mockFtpsClient;

    private FtpsTransmissionClient ftpsClient;
    private TransmissionServerConfig config;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        ftpsClient = new FtpsTransmissionClient() {
            @Override
            protected FTPClient createAndConnectClient(TransmissionServerConfig config) {
                return mockFtpsClient;
            }
        };

        config = new TransmissionServerConfig(
                "ftps.example.com",
                21,
                TransmissionProtocol.FTPS,
                "secureuser",
                "securepass",
                5000,
                true,
                false,
                null,
                null
        );

        when(mockFtpsClient.isConnected()).thenReturn(true);
        when(mockFtpsClient.changeWorkingDirectory(anyString())).thenReturn(true);
    }

    /**
     * Feature: FTPS 協定支援度檢核
     * Scenario: 驗證客戶端僅支援 FTPS (FTP over SSL/TLS) 協定
     * Given: 初始化的 FtpsTransmissionClient 實例
     * When: 傳入 FTPS, FTP, SFTP 與 null 協定進行檢核
     * Then: 僅 FTPS 協定回傳 true，其餘皆回傳 false
     */
    @Test
    @DisplayName("情境：檢核 FTPS 協定支援度判斷")
    void testSupportsProtocol() {
        assertTrue(ftpsClient.supports(TransmissionProtocol.FTPS));
        assertFalse(ftpsClient.supports(TransmissionProtocol.FTP));
        assertFalse(ftpsClient.supports(TransmissionProtocol.SFTP));
        assertFalse(ftpsClient.supports(null));
    }

    /**
     * Feature: FTPS 記憶體檔案上傳作業 (TLS 加密直傳)
     * Scenario: 成功將記憶體中的位元組透過 FTPSClient 加密寫入遠端伺服器 (零磁碟落盤)
     * Given: 記憶體中包含二進位位元組且 FTPSClient 目錄切換與 storeFile 成功
     * When: 執行 uploadFromMemory
     * Then: 呼叫 storeFile 上傳且用畢自動釋放連線
     */
    @Test
    @DisplayName("情境：透過 FTPS 安全加密通道直接從記憶體上傳檔案")
    void testUploadFromMemorySuccess() throws Exception {
        byte[] content = "Secure FTPS Content".getBytes();

        when(mockFtpsClient.changeWorkingDirectory("/secure/upload")).thenReturn(true);
        when(mockFtpsClient.storeFile(eq("/secure/upload/doc.enc"), any(InputStream.class))).thenReturn(true);

        ftpsClient.uploadFromMemory(
                config,
                content,
                RemoteFilePath.of("/secure/upload"),
                "doc.enc"
        );

        verify(mockFtpsClient).storeFile(eq("/secure/upload/doc.enc"), any(InputStream.class));
        verify(mockFtpsClient).logout();
        verify(mockFtpsClient).disconnect();
    }

    /**
     * Feature: FTPS 檔案上傳作業
     * Scenario: 成功透過 FTPSClient 上傳實體本機檔案
     * Given: 本機存在實體檔案且 storeFile 成功
     * When: 執行 upload
     * Then: 檔案成功上傳
     */
    @Test
    @DisplayName("情境：透過 FTPS 正常上傳本機檔案至遠端伺服器")
    void testUploadSuccess() throws Exception {
        Path localFile = tempDir.resolve("secure_local.txt");
        Files.writeString(localFile, "Confidential Data");

        when(mockFtpsClient.changeWorkingDirectory("/ftps/dir")).thenReturn(true);
        when(mockFtpsClient.storeFile(eq("/ftps/dir/secure_local.txt"), any(InputStream.class))).thenReturn(true);

        ftpsClient.upload(
                config,
                LocalFilePath.of(localFile.toString()),
                RemoteFilePath.of("/ftps/dir"),
                "secure_local.txt"
        );

        verify(mockFtpsClient).storeFile(eq("/ftps/dir/secure_local.txt"), any(InputStream.class));
    }

    /**
     * Feature: FTPS 檔案下載作業
     * Scenario: 成功自 FTPS 伺服器下載檔案至本機
     * Given: retrieveFile 回傳 true
     * When: 執行 download
     * Then: 本機目標檔案成功建立
     */
    @Test
    @DisplayName("情境：透過 FTPS 安全通道下載檔案至本機")
    void testDownloadSuccess() throws Exception {
        Path localTarget = tempDir.resolve("ftps_downloaded.txt");

        when(mockFtpsClient.retrieveFile(eq("/ftps/remote.txt"), any(OutputStream.class))).thenReturn(true);

        ftpsClient.download(
                config,
                RemoteFilePath.of("/ftps/remote.txt"),
                LocalFilePath.of(localTarget.toString())
        );

        verify(mockFtpsClient).retrieveFile(eq("/ftps/remote.txt"), any(OutputStream.class));
    }

    /**
     * Feature: FTPS 遠端清單查詢
     * Scenario: 查詢 FTPS 目錄下的檔案清單
     * Given: FTPS 伺服器回傳 FTPFile 陣列
     * When: 執行 listFiles
     * Then: 正確解析出 RemoteFileInfo 清單
     */
    @Test
    @DisplayName("情境：查詢 FTPS 遠端目錄檔案清單")
    void testListFiles() throws Exception {
        FTPFile ftpFile = new FTPFile();
        ftpFile.setName("secure_audit.log");
        ftpFile.setType(FTPFile.FILE_TYPE);
        ftpFile.setSize(512L);

        when(mockFtpsClient.listFiles("/ftps/logs")).thenReturn(new FTPFile[]{ftpFile});

        List<RemoteFileInfo> files = ftpsClient.listFiles(config, RemoteFilePath.of("/ftps/logs"));

        assertEquals(1, files.size());
        assertEquals("secure_audit.log", files.get(0).fileName());
        assertEquals(512L, files.get(0).size());
    }

    /**
     * Feature: FTPS 連線測試
     * Scenario: 測試 FTPS 伺服器連線狀態
     * Given: 連線回呼成功
     * When: 呼叫 testConnection
     * Then: 回傳 true
     */
    @Test
    @DisplayName("情境：FTPS 連線測試成功回傳 true")
    void testTestConnection() {
        boolean connected = ftpsClient.testConnection(config);
        assertTrue(connected);
    }

    /**
     * Feature: FTPS 連線建立異常轉譯
     * Scenario: 原始 FtpsTransmissionClient 連線失敗時拋出 TransmissionConnectionException
     * Given: 連線至無效的主機與連接埠
     * When: 執行連線
     * Then: 拋出 TransmissionConnectionException
     */
    @Test
    @DisplayName("情境：底層 FTPS TLS 連線失敗時轉譯為 TransmissionConnectionException")
    void testRealClientConnectFailure() {
        FtpsTransmissionClient realClient = new FtpsTransmissionClient();
        TransmissionServerConfig invalidConfig = new TransmissionServerConfig(
                "127.0.0.1",
                65531,
                TransmissionProtocol.FTPS,
                "user",
                "pass",
                500,
                true,
                false,
                null,
                null
        );

        assertThrows(TransmissionConnectionException.class, () ->
                realClient.uploadFromMemory(
                        invalidConfig,
                        "secure data".getBytes(),
                        RemoteFilePath.of("/test"),
                        "test.txt"
                )
        );
    }
}
