package com.example.demo.infrastructure.adapter;

import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.example.demo.infrastructure.transmission.factory.TransmissionClientFactory;
import com.example.demo.infrastructure.transmission.client.TransmissionProtocolClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 檔案傳輸閘道適配器測試 (FileTransmissionGatewayAdapterTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 涵蓋 Adapter 實作方法之客戶端工廠調度與委派呼叫；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class FileTransmissionGatewayAdapterTest {

    @Mock
    private TransmissionClientFactory clientFactory;

    @Mock
    private TransmissionProtocolClient mockClient;

    private FileTransmissionGatewayAdapter adapter;

    private TransmissionServerConfig config;

    @BeforeEach
    void setUp() {
        adapter = new FileTransmissionGatewayAdapter(clientFactory);
        config = TransmissionServerConfig.of("example.com", 22, TransmissionProtocol.SFTP, "user", "pass");
        when(clientFactory.getClient(TransmissionProtocol.SFTP)).thenReturn(mockClient);
    }

    /**
     * Feature: 檔案傳輸閘道操作委派
     * Scenario: 呼叫 upload 時正確委派至工廠解析之客戶端
     * Given: 準備連線配置與路徑
     * When: 呼叫 adapter.upload
     * Then: 驗證 mockClient.upload 被正確調用
     */
    @Test
    @DisplayName("情境：上傳操作正確委派至對應客戶端")
    void testUploadDelegation() {
        LocalFilePath local = LocalFilePath.of("/local/file.txt");
        RemoteFilePath remote = RemoteFilePath.of("/remote/data");

        adapter.upload(config, local, remote, "file.txt");

        verify(mockClient).upload(config, local, remote, "file.txt");
    }

    /**
     * Feature: 檔案傳輸閘道操作委派
     * Scenario: 呼叫 upload (記憶體位元組) 時正確委派至工廠解析之客戶端
     * Given: 準備連線配置、記憶體 byte 陣列與遠端目錄
     * When: 呼叫 adapter.upload (byte[] 版本)
     * Then: 驗證 mockClient.upload (byte[] 版本) 被正確調用
     */
    @Test
    @DisplayName("情境：記憶體檔案上傳操作正確委派至對應客戶端")
    void testUploadMemoryDelegation() {
        byte[] content = "Hello Memory Transmission".getBytes();
        RemoteFilePath remote = RemoteFilePath.of("/remote/data");

        adapter.uploadFromMemory(config, content, remote, "memory_file.txt");

        verify(mockClient).uploadFromMemory(config, content, remote, "memory_file.txt");
    }

    /**
     * Feature: 檔案傳輸閘道操作委派
     * Scenario: 呼叫 download 時正確委派至工廠解析之客戶端
     * Given: 準備連線配置與下載路徑
     * When: 呼叫 adapter.download
     * Then: 驗證 mockClient.download 被正確調用
     */
    @Test
    @DisplayName("情境：下載操作正確委派至對應客戶端")
    void testDownloadDelegation() {
        RemoteFilePath remote = RemoteFilePath.of("/remote/file.txt");
        LocalFilePath local = LocalFilePath.of("/local/file.txt");

        adapter.download(config, remote, local);

        verify(mockClient).download(config, remote, local);
    }

    /**
     * Feature: 檔案傳輸閘道操作委派
     * Scenario: 呼叫 syncDirectory 時正確委派至工廠解析之客戶端並回傳傳輸數量
     * Given: 準備連線配置與同步參數
     * When: 呼叫 adapter.syncDirectory
     * Then: 驗證 mockClient.syncDirectory 被正確調用並回傳正確結果
     */
    @Test
    @DisplayName("情境：目錄同步操作正確委派至對應客戶端")
    void testSyncDirectoryDelegation() {
        LocalFilePath local = LocalFilePath.of("/local/dir");
        RemoteFilePath remote = RemoteFilePath.of("/remote/dir");
        when(mockClient.syncDirectory(config, local, remote, SyncDirection.LOCAL_TO_REMOTE, true)).thenReturn(5);

        int result = adapter.syncDirectory(config, local, remote, SyncDirection.LOCAL_TO_REMOTE, true);

        assertEquals(5, result);
        verify(mockClient).syncDirectory(config, local, remote, SyncDirection.LOCAL_TO_REMOTE, true);
    }

    /**
     * Feature: 檔案傳輸閘道操作委派
     * Scenario: 呼叫 listFiles, deleteFile, deleteDirectory 與 testConnection 時正確委派
     * Given: 準備各操作必要參數
     * When: 呼叫各查詢與管理方法
     * Then: 驗證各底層委派方法均被執行
     */
    @Test
    @DisplayName("情境：清單查詢、刪除與連線測試正確委派")
    void testOtherOperationsDelegation() {
        RemoteFilePath remotePath = RemoteFilePath.of("/remote/data");
        when(mockClient.listFiles(config, remotePath)).thenReturn(List.of());
        when(mockClient.deleteFile(config, remotePath)).thenReturn(true);
        when(mockClient.deleteDirectory(config, remotePath)).thenReturn(true);
        when(mockClient.testConnection(config)).thenReturn(true);

        assertEquals(0, adapter.listFiles(config, remotePath).size());
        assertTrue(adapter.deleteFile(config, remotePath));
        assertTrue(adapter.deleteDirectory(config, remotePath));
        assertTrue(adapter.testConnection(config));

        verify(mockClient).listFiles(config, remotePath);
        verify(mockClient).deleteFile(config, remotePath);
        verify(mockClient).deleteDirectory(config, remotePath);
        verify(mockClient).testConnection(config);
    }

    /**
     * Feature: 檔案傳輸閘道操作委派
     * Scenario: 呼叫 existsFile 與 moveFile 時正確委派至工廠解析之客戶端
     * Given: 準備連線配置、遠端檔案路徑與目標路徑
     * When: 呼叫 adapter.existsFile 與 adapter.moveFile
     * Then: 驗證 mockClient.existsFile 與 mockClient.moveFile 被正確調用且回傳預期結果
     */
    @Test
    @DisplayName("情境：檔案存在檢查與移動重命名操作正確委派至對應客戶端")
    void testExistsFileAndMoveFileDelegation() {
        RemoteFilePath sourcePath = RemoteFilePath.of("/remote/data/file.txt");
        RemoteFilePath targetPath = RemoteFilePath.of("/remote/data/file_20261018.bak");

        when(mockClient.existsFile(config, sourcePath)).thenReturn(true);

        boolean exists = adapter.existsFile(config, sourcePath);
        assertTrue(exists);
        verify(mockClient).existsFile(config, sourcePath);

        adapter.moveFile(config, sourcePath, targetPath);
        verify(mockClient).moveFile(config, sourcePath, targetPath);
    }
}
