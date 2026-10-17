package com.example.demo.infrastructure.adapter;

import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * 檔案傳輸任務排程適配器測試 (FileTransmissionTaskSchedulerAdapterTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 驗證 Adapter 實作方法之執行緒池委派呼叫；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class FileTransmissionTaskSchedulerAdapterTest {

    @Mock
    private Executor mockExecutor;

    private FileTransmissionTaskSchedulerAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FileTransmissionTaskSchedulerAdapter(mockExecutor);
    }

    /**
     * Feature: 任務非同步排程委派
     * Scenario: 呼叫 schedule 時正確委派至底層 Executor 執行
     * Given: 準備一個 Runnable 任務
     * When: 呼叫 adapter.schedule(task)
     * Then: 驗證 mockExecutor.execute(task) 被正確調用
     */
    @Test
    @DisplayName("情境：排程任務成功委派至底層執行緒池")
    void testScheduleDelegatesToExecutor() {
        Runnable mockTask = () -> {};

        adapter.schedule(mockTask);

        verify(mockExecutor).execute(mockTask);
    }
}
