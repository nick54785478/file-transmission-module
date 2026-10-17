package com.example.demo.application.service;

import com.example.demo.application.port.out.FileTransmissionGatewayPort;
import com.example.demo.application.port.out.FileTransmissionTrackerPort;
import com.example.demo.application.shared.query.ListRemoteFilesQuery;
import com.example.demo.application.shared.view.RemoteFileGottenView;
import com.example.demo.application.shared.vo.RemoteFileInfo;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 遠端檔案查詢使用案例測試 (ListRemoteFilesUseCaseTest)
 * <p>
 * 遵循測試規範：
 * 1. 涵蓋 UseCase 查詢流程與投影轉換驗證；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ListRemoteFilesUseCaseTest {

    @Mock
    private FileTransmissionGatewayPort fileTransmissionGatewayPort;

    @Mock
    private FileTransmissionTrackerPort fileTransmissionTrackerPort;

    private FileTransmissionQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new FileTransmissionQueryService(fileTransmissionGatewayPort, fileTransmissionTrackerPort);
    }

    /**
     * Feature: 遠端檔案查詢業務流程
     * Scenario: 成功查詢遠端伺服器目錄下的檔案清單並轉換為檢視投影
     * Given: 準備有效之查詢物件與 Mock 遠端檔案資訊清單
     * When: 執行 ListRemoteFilesUseCase 的 listRemoteFiles 方法
     * Then: 回傳包含檔案名稱、大小與型態之 RemoteFileGottenView 清單
     */
    @Test
    @DisplayName("情境：成功查詢遠端目錄檔案並轉換為 RemoteFileGottenView")
    void testListRemoteFilesSuccess() {
        TransmissionServerConfig serverConfig = TransmissionServerConfig.of(
                "sftp.example.com",
                22,
                TransmissionProtocol.SFTP,
                "sftpuser",
                "sftppass"
        );

        ListRemoteFilesQuery query = new ListRemoteFilesQuery(serverConfig, RemoteFilePath.of("/remote/data"));

        List<RemoteFileInfo> mockFiles = List.of(
                new RemoteFileInfo("file1.txt", RemoteFilePath.of("/remote/data/file1.txt"), 1024L, 1600000000000L, false),
                new RemoteFileInfo("subfolder", RemoteFilePath.of("/remote/data/subfolder"), 0L, 1600000000000L, true)
        );

        when(fileTransmissionGatewayPort.listFiles(serverConfig, RemoteFilePath.of("/remote/data")))
                .thenReturn(mockFiles);

        List<RemoteFileGottenView> views = queryService.listRemoteFiles(query);

        assertNotNull(views);
        assertEquals(2, views.size());
        assertEquals("file1.txt", views.get(0).getFileName());
        assertFalse(views.get(0).isDirectory());
        assertEquals(1024L, views.get(0).getSize());

        assertEquals("subfolder", views.get(1).getFileName());
        assertTrue(views.get(1).isDirectory());
    }
}
