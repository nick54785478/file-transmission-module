package com.example.demo.config.config;

import java.util.Objects;
import java.util.concurrent.Executor;

import com.example.demo.config.properties.AsyncTransmissionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 檔案傳輸非同步執行緒池配置類 (AsyncTransmissionConfig - Configuration Layer)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>位於配置層 (<code>com.example.demo.config</code>)。</li>
 *   <li>啟用 Spring 非同步機制 (<code>@EnableAsync</code>) 與屬性綁定 (<code>@EnableConfigurationProperties</code>)。</li>
 *   <li>配置專屬檔案傳輸任務執行緒池 <code>fileTransmissionTaskExecutor</code>，專職負責非同步長任務之 I/O 傳輸，
 *       避免阻塞 Web 請求執行緒 (Tomcat worker threads)。</li>
 *   <li>所有參數由 {@link AsyncTransmissionProperties} 集中封裝並自 <code>application.properties</code> 注入。</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(AsyncTransmissionProperties.class)
public class AsyncTransmissionConfig {

    private final AsyncTransmissionProperties properties;

    /**
     * 無參建構子：以預設配置屬性初始化
     */
    public AsyncTransmissionConfig() {
        this(new AsyncTransmissionProperties());
    }

    /**
     * 屬性注入建構子
     *
     * @param properties 執行緒池配置屬性
     */
    public AsyncTransmissionConfig(AsyncTransmissionProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties 不能為空");
    }

    /**
     * 註冊專屬之非同步任務執行緒池 Bean
     *
     * @return 已初始化之 ThreadPoolTaskExecutor
     */
    @Bean(name = "fileTransmissionTaskExecutor")
    public Executor fileTransmissionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setWaitForTasksToCompleteOnShutdown(properties.isWaitForTasksToCompleteOnShutdown());
        executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());
        executor.initialize();
        return executor;
    }

    public AsyncTransmissionProperties getProperties() {
        return properties;
    }
}
