package com.example.demo.application.service;

import com.example.demo.application.port.out.FileTransmissionGatewayPort;
import com.example.demo.application.port.out.FileTransmissionTrackerPort;
import com.example.demo.application.shared.dto.FileTransmissionGottenResult;
import com.example.demo.application.shared.query.GetTransmissionStatusQuery;
import java.util.Optional;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 查詢傳輸狀態使用案例測試 (GetTransmissionStatusUseCaseTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 涵蓋 GetTransmissionStatusUseCase 之查詢流程與結果驗證；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class GetTransmissionStatusUseCaseTest {

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
     * Feature: 傳輸狀態查詢流程
     * Scenario: 依有效 trackingId 成功查得傳輸階段結果
     * Given: 準備存在於 Tracker 的 trackingId
     * When: 執行 getTransmissionStatus 方法
     * Then: 回傳包含狀態與耗時資訊之非空 Optional
     */
    @Test
    @DisplayName("情境：成功依 trackingId 查詢傳輸作業狀態")
    void testGetTransmissionStatusFound() {
        String trackingId = "track-12345";
        GetTransmissionStatusQuery query = GetTransmissionStatusQuery.of(trackingId);

        FileTransmissionGottenResult expectedResult = new FileTransmissionGottenResult(
                trackingId,
                "SFTP",
                "COMPLETED",
                "/local/data.txt",
                "/remote/data.txt",
                "data.txt",
                2048L,
                120L,
                null
        );

        when(fileTransmissionTrackerPort.getTransmissionStatus(trackingId)).thenReturn(Optional.of(expectedResult));

        Optional<FileTransmissionGottenResult> actualResult = queryService.getTransmissionStatus(query);

        assertTrue(actualResult.isPresent());
        assertEquals("track-12345", actualResult.get().transmissionId());
        assertEquals("COMPLETED", actualResult.get().status());
        assertEquals(2048L, actualResult.get().fileSizeBytes());
        verify(fileTransmissionTrackerPort).getTransmissionStatus(trackingId);
    }

    /**
     * Feature: 傳輸狀態查詢流程
     * Scenario: 查詢不存在之 trackingId
     * Given: 準備未存在於 Tracker 的 trackingId
     * When: 執行 getTransmissionStatus 方法
     * Then: 回傳 Optional.empty()
     */
    @Test
    @DisplayName("情境：查詢不存在之 trackingId 回傳 empty")
    void testGetTransmissionStatusNotFound() {
        String trackingId = "non-existent-id";
        GetTransmissionStatusQuery query = GetTransmissionStatusQuery.of(trackingId);

        when(fileTransmissionTrackerPort.getTransmissionStatus(trackingId)).thenReturn(Optional.empty());

        Optional<FileTransmissionGottenResult> actualResult = queryService.getTransmissionStatus(query);

        assertFalse(actualResult.isPresent());
        verify(fileTransmissionTrackerPort).getTransmissionStatus(trackingId);
    }
}
