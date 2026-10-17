package com.example.demo.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 檔案傳輸非同步執行緒池配置屬性載體 (AsyncTransmissionProperties - Configuration Layer)
 * <p>
 * <b>架構與設計職責說明：</b>
 * <ul>
 *   <li>位於專案根目錄下之全域配置層 (config/ 套件)，遵循 AGENTS.md 集中存放架構規範。</li>
 *   <li>透過 <code>@ConfigurationProperties(prefix = "transmission.async")</code> 綁定 <code>application.properties</code> 中的配置項。</li>
 *   <li>提供型別安全的配置物件，且所有欄位均預設與原架構一致之合理預設值：
 *     <ul>
 *       <li>核心執行緒數 (corePoolSize)：預設 4</li>
 *       <li>最大執行緒數 (maxPoolSize)：預設 16</li>
 *       <li>緩衝佇列容量 (queueCapacity)：預設 100</li>
 *       <li>執行緒名稱前綴 (threadNamePrefix)：預設 "transmission-async-"</li>
 *       <li>關機時等待任務完成 (waitForTasksToCompleteOnShutdown)：預設 true</li>
 *       <li>關機等待終止逾時秒數 (awaitTerminationSeconds)：預設 30</li>
 *     </ul>
 *   </li>
 * </ul>
 * </p>
 */
@ConfigurationProperties(prefix = "transmission.async")
public class AsyncTransmissionProperties {

    /**
     * 核心執行緒數，預設 4
     */
    private int corePoolSize = 4;

    /**
     * 最大執行緒數，預設 16
     */
    private int maxPoolSize = 16;

    /**
     * 緩衝佇列容量，預設 100
     */
    private int queueCapacity = 100;

    /**
     * 執行緒名稱前綴，預設 "transmission-async-"
     */
    private String threadNamePrefix = "transmission-async-";

    /**
     * 關機時是否等待進行中任務完成，預設 true
     */
    private boolean waitForTasksToCompleteOnShutdown = true;

    /**
     * 關機最大等待終止秒數，預設 30
     */
    private int awaitTerminationSeconds = 30;

    /**
     * 無參建構子：保留所有欄位之預設值
     */
    public AsyncTransmissionProperties() {
    }

    /**
     * 全參建構子：提供測試與程式化指定配置
     *
     * @param corePoolSize 核心執行緒數
     * @param maxPoolSize 最大執行緒數
     * @param queueCapacity 緩衝佇列容量
     * @param threadNamePrefix 執行緒名稱前綴
     * @param waitForTasksToCompleteOnShutdown 關機時是否等待進行中任務完成
     * @param awaitTerminationSeconds 關機最大等待終止秒數
     */
    public AsyncTransmissionProperties(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            String threadNamePrefix,
            boolean waitForTasksToCompleteOnShutdown,
            int awaitTerminationSeconds
    ) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
        this.threadNamePrefix = threadNamePrefix;
        this.waitForTasksToCompleteOnShutdown = waitForTasksToCompleteOnShutdown;
        this.awaitTerminationSeconds = awaitTerminationSeconds;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    public boolean isWaitForTasksToCompleteOnShutdown() {
        return waitForTasksToCompleteOnShutdown;
    }

    public void setWaitForTasksToCompleteOnShutdown(boolean waitForTasksToCompleteOnShutdown) {
        this.waitForTasksToCompleteOnShutdown = waitForTasksToCompleteOnShutdown;
    }

    public int getAwaitTerminationSeconds() {
        return awaitTerminationSeconds;
    }

    public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
        this.awaitTerminationSeconds = awaitTerminationSeconds;
    }
}
