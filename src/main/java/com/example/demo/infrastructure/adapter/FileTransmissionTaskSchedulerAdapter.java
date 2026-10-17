package com.example.demo.infrastructure.adapter;

import com.example.demo.application.port.out.FileTransmissionTaskSchedulerPort;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 檔案傳輸任務排程基礎設施適配器 (FileTransmissionTaskSchedulerAdapter - Outbound Adapter)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>out : ... + Adapter</code> (AGENTS.md 3-9)。</li>
 *   <li>實作應用層 Outbound Port ({@link FileTransmissionTaskSchedulerPort})。</li>
 *   <li>作為基礎設施層出入口，注入配置層提供的 <code>@Qualifier("fileTransmissionTaskExecutor") Executor</code> 執行緒池，
 *       將非同步任務委派至 Spring 管理的非同步執行緒池中執行。</li>
 *   <li>將 Spring 框架註解 <code>@Qualifier</code> 與執行緒池 Bean 名稱完全封裝於基礎設施層，保證應用層純潔度。</li>
 * </ul>
 * </p>
 */
@Component
public class FileTransmissionTaskSchedulerAdapter implements FileTransmissionTaskSchedulerPort {

    private final Executor taskExecutor;

    /**
     * 建構任務排程適配器並注入非同步執行緒池
     *
     * @param taskExecutor 配置層之專屬非同步傳輸執行緒池
     */
    public FileTransmissionTaskSchedulerAdapter(@Qualifier("fileTransmissionTaskExecutor") Executor taskExecutor) {
        this.taskExecutor = Objects.requireNonNull(taskExecutor, "taskExecutor 不能為空");
    }

    @Override
    public void schedule(Runnable task) {
        Objects.requireNonNull(task, "task 不能為空");
        taskExecutor.execute(task);
    }
}
