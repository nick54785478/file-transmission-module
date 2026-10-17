package com.example.demo.infrastructure.transmission.client;

import com.example.demo.application.shared.exception.FileTransmissionException;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import java.util.List;

import com.example.demo.infrastructure.transmission.factory.TransmissionClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 傳輸協定客戶端工廠測試 (TransmissionClientFactoryTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 驗證 Factory 註冊、索引與依協定解析對應 Client 的邏輯；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TransmissionClientFactoryTest {

    @Mock
    private TransmissionProtocolClient ftpClient;

    @Mock
    private TransmissionProtocolClient ftpsClient;

    @Mock
    private TransmissionProtocolClient sftpClient;

    private TransmissionClientFactory factory;

    @BeforeEach
    void setUp() {
        when(ftpClient.supports(TransmissionProtocol.FTP)).thenReturn(true);
        when(ftpsClient.supports(TransmissionProtocol.FTPS)).thenReturn(true);
        when(sftpClient.supports(TransmissionProtocol.SFTP)).thenReturn(true);

        factory = new TransmissionClientFactory(List.of(ftpClient, ftpsClient, sftpClient));
    }

    /**
     * Feature: 傳輸客戶端工廠路由
     * Scenario: 依據不同傳輸協定正確取得相應的客戶端實例
     * Given: 工廠已註冊 FTP, FTPS, SFTP 客戶端
     * When: 依序查詢各協定之 Client
     * Then: 分別回傳對應的 Mock 客戶端實例
     */
    @Test
    @DisplayName("情境：依協定正確解析並回傳相應之技術客戶端")
    void testGetClientSuccessfully() {
        TransmissionProtocolClient resolvedFtp = factory.getClient(TransmissionProtocol.FTP);
        assertNotNull(resolvedFtp);
        assertEquals(ftpClient, resolvedFtp);

        TransmissionProtocolClient resolvedFtps = factory.getClient(TransmissionProtocol.FTPS);
        assertNotNull(resolvedFtps);
        assertEquals(ftpsClient, resolvedFtps);

        TransmissionProtocolClient resolvedSftp = factory.getClient(TransmissionProtocol.SFTP);
        assertNotNull(resolvedSftp);
        assertEquals(sftpClient, resolvedSftp);
    }

    /**
     * Feature: 傳輸客戶端工廠協定支援檢核
     * Scenario: 驗證 supports 與 getSupportedProtocols 判斷邏輯
     * Given: 包含三種協定客戶端的工廠
     * When: 呼叫 supports 與 getSupportedProtocols
     * Then: 正確回傳 true 且支援清單包含三種協定
     */
    @Test
    @DisplayName("情境：驗證協定支援狀態與支援清單")
    void testSupportsAndGetSupportedProtocols() {
        assertTrue(factory.supports(TransmissionProtocol.FTP));
        assertTrue(factory.supports(TransmissionProtocol.FTPS));
        assertTrue(factory.supports(TransmissionProtocol.SFTP));
        assertFalse(factory.supports(null));

        List<TransmissionProtocol> supported = factory.getSupportedProtocols();
        assertEquals(3, supported.size());
        assertTrue(supported.contains(TransmissionProtocol.FTP));
        assertTrue(supported.contains(TransmissionProtocol.FTPS));
        assertTrue(supported.contains(TransmissionProtocol.SFTP));
    }

    /**
     * Feature: 傳輸客戶端工廠異常處理
     * Scenario: 查詢未支援之協定或傳入空值時拋出相應例外
     * Given: 僅註冊 FTP 客戶端的工廠
     * When: 查詢 SFTP 或傳入 null
     * Then: 分別拋出 FileTransmissionException 與 IllegalArgumentException
     */
    @Test
    @DisplayName("情境：未支援協定或空參數拋出異常")
    void testUnsupportedProtocolThrowsException() {
        TransmissionClientFactory partialFactory = new TransmissionClientFactory(List.of(ftpClient));

        assertThrows(IllegalArgumentException.class, () -> partialFactory.getClient(null));
        assertThrows(FileTransmissionException.class, () -> partialFactory.getClient(TransmissionProtocol.SFTP));
    }
}
