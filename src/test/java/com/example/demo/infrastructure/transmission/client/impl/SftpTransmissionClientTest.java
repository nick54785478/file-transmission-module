package com.example.demo.infrastructure.transmission.client.impl;

import com.example.demo.application.shared.exception.FileTransmissionException;
import com.example.demo.application.shared.exception.TransmissionConnectionException;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFileInfo;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
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
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * SFTP (SSH File Transfer Protocol) 安全協定傳輸客戶端單元測試 (SftpTransmissionClientTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 驗證 {@link SftpTransmissionClient} 的協定支援度、SSH 連線/通道管理、檔案上傳/下載、雙向增量同步、目錄遞迴處理與例外轉譯機制；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SftpTransmissionClientTest {

    @Mock
    private Session mockSession;

    @Mock
    private ChannelSftp mockChannelSftp;

    private SftpTransmissionClient sftpClient;
    private TransmissionServerConfig config;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        sftpClient = new SftpTransmissionClient() {
            @Override
            protected SftpSessionHolder createAndConnectSession(TransmissionServerConfig config) {
                return new SftpSessionHolder(mockSession, mockChannelSftp);
            }
        };

        config = new TransmissionServerConfig(
                "sftp.example.com",
                22,
                TransmissionProtocol.SFTP,
                "sshuser",
                "sshpass",
                5000,
                true,
                false,
                null,
                null
        );

        when(mockChannelSftp.isConnected()).thenReturn(true);
        when(mockSession.isConnected()).thenReturn(true);
    }

    /**
     * Feature: SFTP 協定支援度檢核
     * Scenario: 驗證客戶端僅支援 SFTP 協定
     * Given: 初始化的 SftpTransmissionClient 實例
     * When: 傳入 SFTP, FTP, FTPS 與 null 協定進行檢核
     * Then: 僅 SFTP 協定回傳 true，其餘皆回傳 false
     */
    @Test
    @DisplayName("情境：檢核 SFTP 協定支援度判斷")
    void testSupportsProtocol() {
        assertTrue(sftpClient.supports(TransmissionProtocol.SFTP));
        assertFalse(sftpClient.supports(TransmissionProtocol.FTP));
        assertFalse(sftpClient.supports(TransmissionProtocol.FTPS));
        assertFalse(sftpClient.supports(null));
    }

    /**
     * Feature: SFTP 檔案上傳作業
     * Scenario: 成功將本機檔案透過 SFTP 通道上傳至遠端指定目錄
     * Given: 本機存在實體檔案且遠端目錄切換與 put 串流上傳成功
     * When: 執行 upload 上傳檔案
     * Then: 正確切換工作目錄並呼叫 ChannelSftp.put 上傳檔案，連線安全釋放
     */
    @Test
    @DisplayName("情境：正常上傳本機檔案至遠端 SFTP 伺服器")
    void testUploadSuccess() throws Exception {
        Path localFile = tempDir.resolve("upload_sample.txt");
        Files.writeString(localFile, "SFTP Upload Content");

        sftpClient.upload(
                config,
                LocalFilePath.of(localFile.toString()),
                RemoteFilePath.of("/remote/sftp"),
                "upload_sample.txt"
        );

        verify(mockChannelSftp, atLeastOnce()).cd("/remote/sftp");
        verify(mockChannelSftp).put(any(InputStream.class), eq("upload_sample.txt"));
        verify(mockChannelSftp).disconnect();
        verify(mockSession).disconnect();
    }

    /**
     * Feature: SFTP 檔案上傳遠端目錄自動建立
     * Scenario: 遠端目錄不存在時自動逐層建立並切換
     * Given: 遠端目錄初次切換拋出 SftpException，但 mkdir 成功
     * When: 執行 upload 上傳檔案
     * Then: 自動觸發 mkdir 建立目錄後完成檔案上傳
     */
    @Test
    @DisplayName("情境：遠端 SFTP 目錄不存在時自動遞迴建立目錄")
    void testUploadWithDirectoryCreation() throws Exception {
        Path localFile = tempDir.resolve("nested.txt");
        Files.writeString(localFile, "Nested Content");

        doThrow(new SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "No such directory"))
                .doNothing()
                .when(mockChannelSftp).cd("/remote");

        doThrow(new SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "No such directory"))
                .doThrow(new SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "No such directory"))
                .doNothing()
                .when(mockChannelSftp).cd("/remote/nested");

        sftpClient.upload(
                config,
                LocalFilePath.of(localFile.toString()),
                RemoteFilePath.of("/remote/nested"),
                "nested.txt"
        );

        verify(mockChannelSftp).mkdir("/remote");
        verify(mockChannelSftp).mkdir("/remote/nested");
        verify(mockChannelSftp).put(any(InputStream.class), eq("nested.txt"));
    }

    /**
     * Feature: SFTP 檔案上傳失敗處理
     * Scenario: put 發生 SftpException 時轉譯為 FileTransmissionException
     * Given: put 操作拋出 SftpException
     * When: 執行 upload 上傳檔案
     * Then: 拋出 FileTransmissionException 業務例外
     */
    @Test
    @DisplayName("情境：SFTP 上傳操作拋出底層例外時轉譯為業務例外")
    void testUploadFailureThrowsException() throws Exception {
        Path localFile = tempDir.resolve("error.txt");
        Files.writeString(localFile, "Content");

        doThrow(new SftpException(ChannelSftp.SSH_FX_PERMISSION_DENIED, "Permission denied"))
                .when(mockChannelSftp).put(any(InputStream.class), eq("error.txt"));

        assertThrows(FileTransmissionException.class, () ->
                sftpClient.upload(
                        config,
                        LocalFilePath.of(localFile.toString()),
                        RemoteFilePath.of("/remote"),
                        "error.txt"
                )
        );
    }

    /**
     * Feature: SFTP 記憶體檔案上傳作業
     * Scenario: 成功將記憶體中的位元組陣列直接寫入遠端 SFTP 伺服器 (零磁碟落盤)
     * Given: 有效的位元組陣列與遠端路徑
     * When: 執行 uploadFromMemory
     * Then: 透過 ByteArrayInputStream 與 ChannelSftp.put 完成上傳
     */
    @Test
    @DisplayName("情境：成功從記憶體直接上傳位元組至遠端 SFTP")
    void testUploadFromMemorySuccess() throws Exception {
        byte[] content = "Raw memory bytes for SFTP".getBytes();

        sftpClient.uploadFromMemory(
                config,
                content,
                RemoteFilePath.of("/remote/mem"),
                "memory_file.bin"
        );

        verify(mockChannelSftp, atLeastOnce()).cd("/remote/mem");
        verify(mockChannelSftp).put(any(InputStream.class), eq("memory_file.bin"));
    }

    /**
     * Feature: SFTP 記憶體上傳空值防禦
     * Scenario: 傳入 null 位元組時拋出 NullPointerException
     * Given: content 為 null
     * When: 執行 uploadFromMemory
     * Then: 拋出 NullPointerException
     */
    @Test
    @DisplayName("情境：SFTP 記憶體檔案上傳內容為空時防禦性阻擋")
    void testUploadFromMemoryNullContent() {
        assertThrows(NullPointerException.class, () ->
                sftpClient.uploadFromMemory(
                        config,
                        null,
                        RemoteFilePath.of("/remote"),
                        "file.txt"
                )
        );
    }

    /**
     * Feature: SFTP 檔案下載作業
     * Scenario: 成功自遠端 SFTP 伺服器下載檔案至本機目標路徑
     * Given: 遠端檔案存在，ChannelSftp.get 成功寫入本機輸出串流
     * When: 執行 download
     * Then: 本機目標檔案成功建立且內容相符
     */
    @Test
    @DisplayName("情境：成功自遠端 SFTP 伺服器下載檔案至本機")
    void testDownloadSuccess() throws Exception {
        Path targetLocal = tempDir.resolve("sftp_downloaded.txt");

        doAnswer(invocation -> {
            OutputStream os = invocation.getArgument(1);
            os.write("SFTP Downloaded Payload".getBytes());
            return null;
        }).when(mockChannelSftp).get(eq("/remote/payload.txt"), any(OutputStream.class));

        sftpClient.download(
                config,
                RemoteFilePath.of("/remote/payload.txt"),
                LocalFilePath.of(targetLocal.toString())
        );

        assertTrue(Files.exists(targetLocal));
        assertEquals("SFTP Downloaded Payload", Files.readString(targetLocal));
    }

    /**
     * Feature: SFTP 檔案下載失敗處理
     * Scenario: ChannelSftp.get 拋出 SftpException 時轉譯為 FileTransmissionException
     * Given: 遠端檔案不存在，get 拋出 SSH_FX_NO_SUCH_FILE
     * When: 執行 download
     * Then: 拋出 FileTransmissionException
     */
    @Test
    @DisplayName("情境：SFTP 檔案下載失敗時拋出業務例外")
    void testDownloadFailureThrowsException() throws Exception {
        Path targetLocal = tempDir.resolve("missing.txt");

        doThrow(new SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "File not found"))
                .when(mockChannelSftp).get(eq("/remote/missing.txt"), any(OutputStream.class));

        assertThrows(FileTransmissionException.class, () ->
                sftpClient.download(
                        config,
                        RemoteFilePath.of("/remote/missing.txt"),
                        LocalFilePath.of(targetLocal.toString())
                )
        );
    }

    /**
     * Feature: SFTP 遠端檔案清單查詢
     * Scenario: 成功列出遠端檔案並正確過濾 "." 與 ".."
     * Given: 遠端目錄包含 "."、".." 與一般檔案 "audit.log"
     * When: 執行 listFiles
     * Then: 回傳清單僅包含 "audit.log"，時間戳記正確換算為毫秒
     */
    @Test
    @DisplayName("情境：查詢 SFTP 遠端目錄清單並過濾導航節點")
    void testListFilesFilteringDotNodes() throws Exception {
        Vector<ChannelSftp.LsEntry> entries = new Vector<>();

        ChannelSftp.LsEntry dotEntry = mock(ChannelSftp.LsEntry.class);
        when(dotEntry.getFilename()).thenReturn(".");
        entries.add(dotEntry);

        ChannelSftp.LsEntry dotDotEntry = mock(ChannelSftp.LsEntry.class);
        when(dotDotEntry.getFilename()).thenReturn("..");
        entries.add(dotDotEntry);

        ChannelSftp.LsEntry fileEntry = mock(ChannelSftp.LsEntry.class);
        when(fileEntry.getFilename()).thenReturn("audit.log");
        SftpATTRS attrs = mock(SftpATTRS.class);
        when(attrs.getSize()).thenReturn(4096L);
        when(attrs.getMTime()).thenReturn(1700000);
        when(attrs.isDir()).thenReturn(false);
        when(fileEntry.getAttrs()).thenReturn(attrs);
        entries.add(fileEntry);

        when(mockChannelSftp.ls("/remote/logs")).thenReturn(entries);

        List<RemoteFileInfo> files = sftpClient.listFiles(config, RemoteFilePath.of("/remote/logs"));

        assertEquals(1, files.size());
        RemoteFileInfo info = files.get(0);
        assertEquals("audit.log", info.fileName());
        assertEquals(4096L, info.size());
        assertEquals(1700000000L, info.lastModifiedMillis());
        assertFalse(info.isDirectory());
    }

    /**
     * Feature: 刪除遠端單一檔案
     * Scenario: 呼叫 deleteFile 刪除遠端檔案成功
     * Given: rm 成功執行
     * When: 執行 deleteFile
     * Then: 回傳 true
     */
    @Test
    @DisplayName("情境：成功刪除遠端 SFTP 檔案")
    void testDeleteFileSuccess() throws Exception {
        boolean deleted = sftpClient.deleteFile(config, RemoteFilePath.of("/remote/delete.me"));
        assertTrue(deleted);
        verify(mockChannelSftp).rm("/remote/delete.me");
    }

    /**
     * Feature: 刪除遠端單一檔案異常容錯
     * Scenario: rm 拋出 SftpException 時安全回傳 false
     * Given: mockChannelSftp.rm 拋出 SftpException
     * When: 執行 deleteFile
     * Then: 安全回傳 false
     */
    @Test
    @DisplayName("情境：刪除遠端 SFTP 檔案發生異常時安全回傳 false")
    void testDeleteFileSftpExceptionReturnsFalse() throws Exception {
        doThrow(new SftpException(ChannelSftp.SSH_FX_FAILURE, "Cannot remove file"))
                .when(mockChannelSftp).rm("/remote/locked.txt");

        boolean deleted = sftpClient.deleteFile(config, RemoteFilePath.of("/remote/locked.txt"));
        assertFalse(deleted);
    }

    /**
     * Feature: 遞迴刪除遠端 SFTP 目錄
     * Scenario: 逐層刪除目錄內檔案並移除目錄本身
     * Given: 遠端目錄包含一個子檔案
     * When: 執行 deleteDirectory
     * Then: 先呼叫 rm 刪除檔案，再呼叫 rmdir 刪除目錄
     */
    @Test
    @DisplayName("情境：遞迴清空並刪除遠端 SFTP 目錄")
    void testDeleteDirectorySuccess() throws Exception {
        Vector<ChannelSftp.LsEntry> entries = new Vector<>();
        ChannelSftp.LsEntry childEntry = mock(ChannelSftp.LsEntry.class);
        when(childEntry.getFilename()).thenReturn("child.log");
        SftpATTRS attrs = mock(SftpATTRS.class);
        when(attrs.isDir()).thenReturn(false);
        when(childEntry.getAttrs()).thenReturn(attrs);
        entries.add(childEntry);

        when(mockChannelSftp.ls("/remote/dir")).thenReturn(entries);

        boolean result = sftpClient.deleteDirectory(config, RemoteFilePath.of("/remote/dir"));
        assertTrue(result);

        verify(mockChannelSftp).rm("/remote/dir/child.log");
        verify(mockChannelSftp).rmdir("/remote/dir");
    }

    /**
     * Feature: 檢查遠端 SFTP 檔案是否存在
     * Scenario: stat 回傳有效非目錄屬性時回傳 true
     * Given: sftp.stat 回傳一般檔案屬性
     * When: 執行 existsFile
     * Then: 回傳 true
     */
    @Test
    @DisplayName("情境：正確判斷遠端 SFTP 檔案是否存在")
    void testExistsFile() throws Exception {
        SftpATTRS attrs = mock(SftpATTRS.class);
        when(attrs.isDir()).thenReturn(false);
        when(mockChannelSftp.stat("/remote/exist.txt")).thenReturn(attrs);

        boolean exists = sftpClient.existsFile(config, RemoteFilePath.of("/remote/exist.txt"));
        assertTrue(exists);
    }

    /**
     * Feature: 移動或重新命名遠端 SFTP 檔案
     * Scenario: 呼叫 rename 成功移動檔案
     * Given: rename 成功執行
     * When: 執行 moveFile
     * Then: 正確呼叫 sftp.rename
     */
    @Test
    @DisplayName("情境：成功移動與重新命名遠端 SFTP 檔案")
    void testMoveFileSuccess() throws Exception {
        sftpClient.moveFile(
                config,
                RemoteFilePath.of("/remote/src.txt"),
                RemoteFilePath.of("/remote/dest.txt")
        );

        verify(mockChannelSftp).rename("/remote/src.txt", "/remote/dest.txt");
    }

    /**
     * Feature: 測試 SFTP 連線狀態
     * Scenario: 驗證 SFTP 客戶端連線探測功能
     * Given: 能夠成功連線
     * When: 呼叫 testConnection
     * Then: 回傳 true
     */
    @Test
    @DisplayName("情境：SFTP 連線測試成功回傳 true")
    void testTestConnectionSuccess() {
        boolean connected = sftpClient.testConnection(config);
        assertTrue(connected);
    }

    /**
     * Feature: 本地至遠端目錄增量同步 (LOCAL_TO_REMOTE)
     * Scenario: 本機檔案修改時間比遠端更新時執行增量上傳
     * Given: 本機存在 1 個新檔案，遠端無此檔案
     * When: 執行 syncDirectory (LOCAL_TO_REMOTE)
     * Then: 上傳該檔案並回傳傳輸檔案數 1
     */
    @Test
    @DisplayName("情境：本地至遠端 SFTP 目錄智慧增量比對同步")
    void testSyncDirectoryLocalToRemote() throws Exception {
        Path syncDir = tempDir.resolve("local_sftp_sync");
        Files.createDirectories(syncDir);
        Path newFile = syncDir.resolve("sync_item.txt");
        Files.writeString(newFile, "Sync Item Content");

        Vector<ChannelSftp.LsEntry> remoteEntries = new Vector<>();
        when(mockChannelSftp.ls("/remote/sync")).thenReturn(remoteEntries);

        int count = sftpClient.syncDirectory(
                config,
                LocalFilePath.of(syncDir.toString()),
                RemoteFilePath.of("/remote/sync"),
                SyncDirection.LOCAL_TO_REMOTE,
                false
        );

        assertEquals(1, count);
        verify(mockChannelSftp, atLeastOnce()).cd("/remote/sync");
        verify(mockChannelSftp).put(any(InputStream.class), eq("sync_item.txt"));
    }

    /**
     * Feature: 遠端至本地目錄增量同步 (REMOTE_TO_LOCAL)
     * Scenario: 遠端存在檔案且本地無此檔案時執行增量下載
     * Given: 遠端存在 1 個檔案，本地目錄為空
     * When: 執行 syncDirectory (REMOTE_TO_LOCAL)
     * Then: 下載該檔案並回傳傳輸檔案數 1
     */
    @Test
    @DisplayName("情境：遠端至本地 SFTP 目錄智慧增量下載同步")
    void testSyncDirectoryRemoteToLocal() throws Exception {
        Path localDir = tempDir.resolve("download_sftp_sync");
        Files.createDirectories(localDir);

        Vector<ChannelSftp.LsEntry> remoteEntries = new Vector<>();
        ChannelSftp.LsEntry entry = mock(ChannelSftp.LsEntry.class);
        when(entry.getFilename()).thenReturn("remote_file.txt");
        SftpATTRS attrs = mock(SftpATTRS.class);
        when(attrs.isDir()).thenReturn(false);
        when(attrs.getMTime()).thenReturn(1700000);
        when(entry.getAttrs()).thenReturn(attrs);
        remoteEntries.add(entry);

        when(mockChannelSftp.ls("/remote/download")).thenReturn(remoteEntries);

        int count = sftpClient.syncDirectory(
                config,
                LocalFilePath.of(localDir.toString()),
                RemoteFilePath.of("/remote/download"),
                SyncDirection.REMOTE_TO_LOCAL,
                false
        );

        assertEquals(1, count);
        verify(mockChannelSftp).get(eq("/remote/download/remote_file.txt"), any(OutputStream.class));
    }

    /**
     * Feature: SFTP 連線異常轉譯
     * Scenario: 原始 SftpTransmissionClient 連線不可達時拋出 TransmissionConnectionException
     * Given: 連線至無效的主機與連接埠
     * When: 執行連線
     * Then: 拋出 TransmissionConnectionException
     */
    @Test
    @DisplayName("情境：底層 SFTP 連線失敗時轉譯為 TransmissionConnectionException")
    void testRealClientConnectFailure() {
        SftpTransmissionClient realClient = new SftpTransmissionClient();
        TransmissionServerConfig invalidConfig = new TransmissionServerConfig(
                "127.0.0.1",
                65532,
                TransmissionProtocol.SFTP,
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
                        "sftp data".getBytes(),
                        RemoteFilePath.of("/test"),
                        "test.txt"
                )
        );
    }
}
