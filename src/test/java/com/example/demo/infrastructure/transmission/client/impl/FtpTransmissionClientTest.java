package com.example.demo.infrastructure.transmission.client.impl;

import com.example.demo.application.shared.exception.FileTransmissionException;
import com.example.demo.application.shared.exception.TransmissionConnectionException;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFileInfo;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.List;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FTP 傳輸協定客戶端單元測試 (FtpTransmissionClientTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 驗證 {@link FtpTransmissionClient} 的協定支援判定、連線生命週期管理、檔案上傳/下載、雙向同步與例外轉譯機制；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FtpTransmissionClientTest {

    @Mock
    private FTPClient mockFtpClient;

    private FtpTransmissionClient ftpClient;
    private TransmissionServerConfig config;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        ftpClient = new FtpTransmissionClient() {
            @Override
            protected FTPClient createAndConnectClient(TransmissionServerConfig config) {
                return mockFtpClient;
            }
        };

        config = new TransmissionServerConfig(
                "ftp.example.com",
                21,
                TransmissionProtocol.FTP,
                "testuser",
                "testpass",
                5000,
                true,
                false,
                null,
                null
        );

        when(mockFtpClient.isConnected()).thenReturn(true);
        when(mockFtpClient.changeWorkingDirectory(anyString())).thenReturn(true);
    }

    /**
     * Feature: FTP 協定支援度檢核
     * Scenario: 驗證客戶端僅支援標準 FTP 協定
     * Given: 初始化的 FtpTransmissionClient 實例
     * When: 傳入 FTP, FTPS, SFTP 與 null 協定進行檢核
     * Then: 僅 FTP 協定回傳 true，其餘皆回傳 false
     */
    @Test
    @DisplayName("情境：檢核 FTP 協定支援度判斷")
    void testSupportsProtocol() {
        assertTrue(ftpClient.supports(TransmissionProtocol.FTP));
        assertFalse(ftpClient.supports(TransmissionProtocol.FTPS));
        assertFalse(ftpClient.supports(TransmissionProtocol.SFTP));
        assertFalse(ftpClient.supports(null));
    }

    /**
     * Feature: 檔案上傳作業
     * Scenario: 成功將本機檔案透過二進位串流寫入遠端 FTP 伺服器
     * Given: 本機存在實體檔案且遠端目錄切換與檔案儲存皆回傳成功
     * When: 執行 upload 上傳檔案
     * Then: 驗證正確切換工作目錄並呼叫 storeFile 上傳二進位串流
     */
    @Test
    @DisplayName("情境：正常上傳本機檔案至遠端 FTP 伺服器")
    void testUploadSuccess() throws Exception {
        Path localFile = tempDir.resolve("sample.txt");
        Files.writeString(localFile, "Hello FTP Transmission");

        when(mockFtpClient.changeWorkingDirectory("/remote/upload")).thenReturn(true);
        when(mockFtpClient.storeFile(eq("/remote/upload/sample.txt"), any(InputStream.class))).thenReturn(true);

        ftpClient.upload(
                config,
                LocalFilePath.of(localFile.toString()),
                RemoteFilePath.of("/remote/upload"),
                "sample.txt"
        );

        verify(mockFtpClient).storeFile(eq("/remote/upload/sample.txt"), any(InputStream.class));
        verify(mockFtpClient).logout();
        verify(mockFtpClient).disconnect();
    }

    /**
     * Feature: 檔案上傳遠端目錄自動建立
     * Scenario: 遠端目錄不存在時自動逐層建立並切換
     * Given: 遠端目錄第一次切換失敗，但發送 makeDirectory 成功
     * When: 執行 upload 上傳檔案
     * Then: 驗證觸發 makeDirectory 建立目錄後順利上傳
     */
    @Test
    @DisplayName("情境：遠端目錄不存在時自動遞迴建立")
    void testUploadWithDirectoryCreation() throws Exception {
        Path localFile = tempDir.resolve("test.pdf");
        Files.writeString(localFile, "PDF Content");

        when(mockFtpClient.changeWorkingDirectory("/remote")).thenReturn(true);
        when(mockFtpClient.changeWorkingDirectory("/remote/sub")).thenReturn(false).thenReturn(true);
        when(mockFtpClient.makeDirectory("/remote/sub")).thenReturn(true);
        when(mockFtpClient.storeFile(eq("/remote/sub/test.pdf"), any(InputStream.class))).thenReturn(true);

        ftpClient.upload(
                config,
                LocalFilePath.of(localFile.toString()),
                RemoteFilePath.of("/remote/sub"),
                "test.pdf"
        );

        verify(mockFtpClient).makeDirectory("/remote/sub");
        verify(mockFtpClient).storeFile(eq("/remote/sub/test.pdf"), any(InputStream.class));
    }

    /**
     * Feature: 檔案上傳失敗處理
     * Scenario: storeFile 回傳 false 時拋出 FileTransmissionException
     * Given: 遠端目錄存在，但 storeFile 寫入失敗
     * When: 執行 upload 上傳檔案
     * Then: 拋出 FileTransmissionException 並包含伺服器回應碼
     */
    @Test
    @DisplayName("情境：檔案上傳因伺服器拒絕而失敗時拋出業務例外")
    void testUploadFailureThrowsException() throws Exception {
        Path localFile = tempDir.resolve("fail.txt");
        Files.writeString(localFile, "Content");

        when(mockFtpClient.changeWorkingDirectory("/remote/dir")).thenReturn(true);
        when(mockFtpClient.storeFile(eq("/remote/dir/fail.txt"), any(InputStream.class))).thenReturn(false);
        when(mockFtpClient.getReplyCode()).thenReturn(550);

        assertThrows(FileTransmissionException.class, () ->
                ftpClient.upload(
                        config,
                        LocalFilePath.of(localFile.toString()),
                        RemoteFilePath.of("/remote/dir"),
                        "fail.txt"
                )
        );
    }

    /**
     * Feature: 記憶體檔案上傳作業
     * Scenario: 成功將記憶體中的位元組陣列直接寫入遠端伺服器 (零磁碟落盤)
     * Given: 有效的位元組陣列與遠端路徑
     * When: 執行 uploadFromMemory
     * Then: 驗證透過 ByteArrayInputStream 傳輸二進位串流至遠端
     */
    @Test
    @DisplayName("情境：成功從記憶體直接上傳位元組至遠端 FTP")
    void testUploadFromMemorySuccess() throws Exception {
        byte[] content = "Memory File Content".getBytes();

        when(mockFtpClient.changeWorkingDirectory("/remote/memory")).thenReturn(true);
        when(mockFtpClient.storeFile(eq("/remote/memory/memory.dat"), any(InputStream.class))).thenReturn(true);

        ftpClient.uploadFromMemory(
                config,
                content,
                RemoteFilePath.of("/remote/memory"),
                "memory.dat"
        );

        verify(mockFtpClient).storeFile(eq("/remote/memory/memory.dat"), any(InputStream.class));
    }

    /**
     * Feature: 記憶體檔案上傳空值防禦
     * Scenario: 傳入 null 位元組時拋出 NullPointerException
     * Given: content 為 null
     * When: 執行 uploadFromMemory
     * Then: 拋出 NullPointerException
     */
    @Test
    @DisplayName("情境：上傳記憶體內容為空時防禦性阻擋")
    void testUploadFromMemoryNullContent() {
        assertThrows(NullPointerException.class, () ->
                ftpClient.uploadFromMemory(
                        config,
                        null,
                        RemoteFilePath.of("/remote"),
                        "file.txt"
                )
        );
    }

    /**
     * Feature: 檔案下載作業
     * Scenario: 成功自遠端下載檔案至本機目標路徑
     * Given: 遠端檔案存在，retrieveFile 回傳 true 並寫入串流
     * When: 執行 download
     * Then: 本機目標檔案成功建立且內容相符
     */
    @Test
    @DisplayName("情境：成功自遠端 FTP 伺服器下載檔案至本機")
    void testDownloadSuccess() throws Exception {
        Path targetLocal = tempDir.resolve("downloaded/result.txt");

        doAnswer(invocation -> {
            OutputStream os = invocation.getArgument(1);
            os.write("Downloaded FTP Content".getBytes());
            return true;
        }).when(mockFtpClient).retrieveFile(eq("/remote/result.txt"), any(OutputStream.class));

        ftpClient.download(
                config,
                RemoteFilePath.of("/remote/result.txt"),
                LocalFilePath.of(targetLocal.toString())
        );

        assertTrue(Files.exists(targetLocal));
        assertEquals("Downloaded FTP Content", Files.readString(targetLocal));
    }

    /**
     * Feature: 檔案下載失敗處理
     * Scenario: retrieveFile 回傳 false 時拋出 FileTransmissionException
     * Given: 遠端檔案不存在或讀取被拒，retrieveFile 回傳 false
     * When: 執行 download
     * Then: 拋出 FileTransmissionException
     */
    @Test
    @DisplayName("情境：下載檔案失敗時拋出業務例外")
    void testDownloadFailureThrowsException() throws Exception {
        Path targetLocal = tempDir.resolve("nonexistent.txt");
        when(mockFtpClient.retrieveFile(eq("/remote/nonexistent.txt"), any(OutputStream.class))).thenReturn(false);
        when(mockFtpClient.getReplyCode()).thenReturn(550);

        assertThrows(FileTransmissionException.class, () ->
                ftpClient.download(
                        config,
                        RemoteFilePath.of("/remote/nonexistent.txt"),
                        LocalFilePath.of(targetLocal.toString())
                )
        );
    }

    /**
     * Feature: 遠端檔案清單查詢
     * Scenario: 成功列出遠端檔案並正確排除 "." 與 ".."
     * Given: 遠端目錄包含 "."、".." 與一般檔案 "data.csv"
     * When: 執行 listFiles
     * Then: 回傳清單僅包含 "data.csv"，排除當前與父目錄項目
     */
    @Test
    @DisplayName("情境：查詢遠端檔案清單並排除目錄自我導航節點")
    void testListFilesFilteringDotNodes() throws Exception {
        FTPFile dot = new FTPFile();
        dot.setName(".");
        dot.setType(FTPFile.DIRECTORY_TYPE);

        FTPFile dotDot = new FTPFile();
        dotDot.setName("..");
        dotDot.setType(FTPFile.DIRECTORY_TYPE);

        FTPFile regularFile = new FTPFile();
        regularFile.setName("data.csv");
        regularFile.setType(FTPFile.FILE_TYPE);
        regularFile.setSize(2048L);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(1700000000000L);
        regularFile.setTimestamp(cal);

        when(mockFtpClient.listFiles("/remote/dir")).thenReturn(new FTPFile[]{dot, dotDot, regularFile});

        List<RemoteFileInfo> files = ftpClient.listFiles(config, RemoteFilePath.of("/remote/dir"));

        assertEquals(1, files.size());
        RemoteFileInfo fileInfo = files.get(0);
        assertEquals("data.csv", fileInfo.fileName());
        assertEquals(2048L, fileInfo.size());
        assertEquals(1700000000000L, fileInfo.lastModifiedMillis());
        assertFalse(fileInfo.isDirectory());
    }

    /**
     * Feature: 刪除遠端單一檔案
     * Scenario: 呼叫 deleteFile 刪除遠端檔案成功
     * Given: 遠端檔案存在且 deleteFile 回傳 true
     * When: 執行 deleteFile
     * Then: 正確回傳 true
     */
    @Test
    @DisplayName("情境：成功刪除遠端 FTP 檔案")
    void testDeleteFileSuccess() throws Exception {
        when(mockFtpClient.deleteFile("/remote/file.log")).thenReturn(true);

        boolean deleted = ftpClient.deleteFile(config, RemoteFilePath.of("/remote/file.log"));
        assertTrue(deleted);
    }

    /**
     * Feature: 刪除遠端單一檔案異常容錯
     * Scenario: 遠端檔案刪除時底層拋出 IOException
     * Given: mockFtpClient.deleteFile 拋出 IOException
     * When: 執行 deleteFile
     * Then: 安全回傳 false 而不向上冒泡未受檢例外
     */
    @Test
    @DisplayName("情境：刪除遠端檔案發生 I/O 異常時安全容錯回傳 false")
    void testDeleteFileIOExceptionReturnsFalse() throws Exception {
        when(mockFtpClient.deleteFile("/remote/error.log")).thenThrow(new IOException("Disk I/O error"));

        boolean deleted = ftpClient.deleteFile(config, RemoteFilePath.of("/remote/error.log"));
        assertFalse(deleted);
    }

    /**
     * Feature: 遞迴刪除遠端目錄
     * Scenario: 成功逐層刪除遠端目錄內子檔案並移除最外層目錄
     * Given: 遠端目錄包含一個子檔案
     * When: 執行 deleteDirectory
     * Then: 依序刪除檔案並移除空目錄，回傳 true
     */
    @Test
    @DisplayName("情境：遞迴清空並刪除遠端目錄及其內部所有檔案")
    void testDeleteDirectorySuccess() throws Exception {
        FTPFile childFile = new FTPFile();
        childFile.setName("child.txt");
        childFile.setType(FTPFile.FILE_TYPE);

        when(mockFtpClient.listFiles("/remote/dir")).thenReturn(new FTPFile[]{childFile});
        when(mockFtpClient.deleteFile("/remote/dir/child.txt")).thenReturn(true);
        when(mockFtpClient.removeDirectory("/remote/dir")).thenReturn(true);

        boolean result = ftpClient.deleteDirectory(config, RemoteFilePath.of("/remote/dir"));
        assertTrue(result);

        verify(mockFtpClient).deleteFile("/remote/dir/child.txt");
        verify(mockFtpClient).removeDirectory("/remote/dir");
    }

    /**
     * Feature: 檢查遠端檔案是否存在
     * Scenario: 依據 listFiles 比對遠端檔案是否存在
     * Given: listFiles 回傳單一比對成功之檔案
     * When: 執行 existsFile
     * Then: 回傳 true
     */
    @Test
    @DisplayName("情境：正確判斷遠端檔案是否存在")
    void testExistsFile() throws Exception {
        FTPFile targetFile = new FTPFile();
        targetFile.setName("exist.txt");
        targetFile.setType(FTPFile.FILE_TYPE);

        when(mockFtpClient.listFiles("/remote/exist.txt")).thenReturn(new FTPFile[]{targetFile});

        boolean exists = ftpClient.existsFile(config, RemoteFilePath.of("/remote/exist.txt"));
        assertTrue(exists);
    }

    /**
     * Feature: 移動或重新命名遠端檔案
     * Scenario: 成功將遠端檔案自來源路徑移動至目標路徑
     * Given: rename 回傳 true 且目標目錄已存在
     * When: 執行 moveFile
     * Then: 呼叫 rename 移動成功
     */
    @Test
    @DisplayName("情境：成功移動與重新命名遠端檔案")
    void testMoveFileSuccess() throws Exception {
        when(mockFtpClient.changeWorkingDirectory("/remote/target")).thenReturn(true);
        when(mockFtpClient.rename("/remote/source.txt", "/remote/target/moved.txt")).thenReturn(true);

        ftpClient.moveFile(
                config,
                RemoteFilePath.of("/remote/source.txt"),
                RemoteFilePath.of("/remote/target/moved.txt")
        );

        verify(mockFtpClient).rename("/remote/source.txt", "/remote/target/moved.txt");
    }

    /**
     * Feature: 測試伺服器連線狀態
     * Scenario: 驗證客戶端連線探測功能
     * Given: 能夠成功連線回呼
     * When: 呼叫 testConnection
     * Then: 回傳 true
     */
    @Test
    @DisplayName("情境：連線測試成功回傳 true")
    void testTestConnectionSuccess() {
        boolean connected = ftpClient.testConnection(config);
        assertTrue(connected);
    }

    /**
     * Feature: 雙向目錄同步作業 (LOCAL_TO_REMOTE)
     * Scenario: 本機目錄同步至遠端 FTP 目錄，增量傳輸檔案並清理遠端多餘檔案
     * Given: 本機存在 1 個檔案，遠端有 1 個多餘檔案，deleteRemoteExtras 為 true
     * When: 執行 syncDirectory (LOCAL_TO_REMOTE)
     * Then: 傳輸檔案並刪除遠端多餘檔案，回傳傳輸總數
     */
    @Test
    @DisplayName("情境：本地至遠端雙向目錄同步與孤兒檔案清理")
    void testSyncDirectoryLocalToRemote() throws Exception {
        Path syncLocalDir = tempDir.resolve("local_sync");
        Files.createDirectories(syncLocalDir);
        Path newFile = syncLocalDir.resolve("new_item.txt");
        Files.writeString(newFile, "New Item");

        when(mockFtpClient.changeWorkingDirectory("/remote/sync")).thenReturn(true);

        FTPFile extraRemoteFile = new FTPFile();
        extraRemoteFile.setName("extra_remote.txt");
        extraRemoteFile.setType(FTPFile.FILE_TYPE);

        when(mockFtpClient.listFiles("/remote/sync")).thenReturn(new FTPFile[]{extraRemoteFile});
        when(mockFtpClient.storeFile(eq("/remote/sync/new_item.txt"), any(InputStream.class))).thenReturn(true);
        when(mockFtpClient.deleteFile("/remote/sync/extra_remote.txt")).thenReturn(true);

        int count = ftpClient.syncDirectory(
                config,
                LocalFilePath.of(syncLocalDir.toString()),
                RemoteFilePath.of("/remote/sync"),
                SyncDirection.LOCAL_TO_REMOTE,
                true
        );

        assertEquals(1, count);
        verify(mockFtpClient).storeFile(eq("/remote/sync/new_item.txt"), any(InputStream.class));
        verify(mockFtpClient).deleteFile("/remote/sync/extra_remote.txt");
    }

    /**
     * Feature: 連線建立異常轉譯
     * Scenario: 原始 FtpTransmissionClient 遭遇連線失敗時拋出 TransmissionConnectionException
     * Given: 連線至無效的主機與連接埠
     * When: 執行實際連線
     * Then: 轉譯為統一傳輸連線例外 TransmissionConnectionException
     */
    @Test
    @DisplayName("情境：底層連線拒絕時轉譯為 TransmissionConnectionException")
    void testRealClientConnectFailure() {
        FtpTransmissionClient realClient = new FtpTransmissionClient();
        TransmissionServerConfig invalidConfig = new TransmissionServerConfig(
                "127.0.0.1",
                65530,
                TransmissionProtocol.FTP,
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
                        "data".getBytes(),
                        RemoteFilePath.of("/test"),
                        "test.txt"
                )
        );
    }
}
