package com.example.demo.config;

import com.example.demo.config.config.AsyncTransmissionConfig;
import com.example.demo.config.properties.AsyncTransmissionProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 非同步執行緒池配置類單元測試 (AsyncTransmissionConfigTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 驗證 AsyncTransmissionProperties 與 AsyncTransmissionConfig 的預設值與自訂注入配置均能正確套用至 ThreadPoolTaskExecutor；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
class AsyncTransmissionConfigTest {

    /**
     * Feature: 非同步執行緒池配置
     * Scenario: AsyncTransmissionProperties 無參建構時應具備預設值
     * Given: 實例化 AsyncTransmissionProperties
     * When: 讀取各項配置預設值
     * Then: corePoolSize 應為 4，maxPoolSize 應為 16，queueCapacity 應為 100，prefix 應為 transmission-async-
     */
    @Test
    @DisplayName("情境：AsyncTransmissionProperties 正確提供預設參數")
    void shouldVerifyPropertiesDefaults() {
        AsyncTransmissionProperties properties = new AsyncTransmissionProperties();

        assertEquals(4, properties.getCorePoolSize());
        assertEquals(16, properties.getMaxPoolSize());
        assertEquals(100, properties.getQueueCapacity());
        assertEquals("transmission-async-", properties.getThreadNamePrefix());
        assertTrue(properties.isWaitForTasksToCompleteOnShutdown());
        assertEquals(30, properties.getAwaitTerminationSeconds());
    }

    /**
     * Feature: 非同步執行緒池配置
     * Scenario: 使用無參建構子時，應正確套用預設核心參數
     * Given: 透過無參建構子實例化 AsyncTransmissionConfig
     * When: 建立並檢驗 fileTransmissionTaskExecutor
     * Then: 產生的 ThreadPoolTaskExecutor 參數應符合預設值 (4, 16)
     */
    @Test
    @DisplayName("情境：無參建構子正確透過預設 Properties 初始化執行緒池")
    void shouldApplyDefaultValues() {
        AsyncTransmissionConfig config = new AsyncTransmissionConfig();

        assertNotNull(config.getProperties());
        assertEquals(4, config.getProperties().getCorePoolSize());
        assertEquals(16, config.getProperties().getMaxPoolSize());

        Executor executor = config.fileTransmissionTaskExecutor();
        assertNotNull(executor);
        assertTrue(executor instanceof ThreadPoolTaskExecutor);
        ThreadPoolTaskExecutor poolExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(4, poolExecutor.getCorePoolSize());
        assertEquals(16, poolExecutor.getMaxPoolSize());
        poolExecutor.shutdown();
    }

    /**
     * Feature: 非同步執行緒池配置
     * Scenario: 傳入自訂 Properties 參數時，應正確設定執行緒池實體
     * Given: 建立自訂之 AsyncTransmissionProperties (8, 32, 200, "custom-prefix-", false, 15)
     * When: 注入 AsyncTransmissionConfig 並建立 fileTransmissionTaskExecutor
     * Then: 產生的 ThreadPoolTaskExecutor 參數應完全相符
     */
    @Test
    @DisplayName("情境：有參建構子正確注入自訂 Properties 並初始化執行緒池")
    void shouldApplyCustomValues() {
        AsyncTransmissionProperties customProps = new AsyncTransmissionProperties(
                8,
                32,
                200,
                "custom-prefix-",
                false,
                15
        );
        AsyncTransmissionConfig config = new AsyncTransmissionConfig(customProps);

        assertEquals(8, config.getProperties().getCorePoolSize());
        assertEquals(32, config.getProperties().getMaxPoolSize());
        assertEquals(200, config.getProperties().getQueueCapacity());
        assertEquals("custom-prefix-", config.getProperties().getThreadNamePrefix());
        assertEquals(false, config.getProperties().isWaitForTasksToCompleteOnShutdown());
        assertEquals(15, config.getProperties().getAwaitTerminationSeconds());

        Executor executor = config.fileTransmissionTaskExecutor();
        assertNotNull(executor);
        assertTrue(executor instanceof ThreadPoolTaskExecutor);
        ThreadPoolTaskExecutor poolExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(8, poolExecutor.getCorePoolSize());
        assertEquals(32, poolExecutor.getMaxPoolSize());
        poolExecutor.shutdown();
    }
}
