package com.example.demo.application.port.out;

/**
 * 檔案傳輸任務非同步排程輸出埠介面 (FileTransmissionTaskSchedulerPort - Outbound Port)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>out : ... + Port</code> (AGENTS.md 3-8)。</li>
 *   <li>六角形架構 Outbound Port，作為應用層對外要求非同步背景任務派發與排程的標準抽象合約。</li>
 *   <li>隔離具體的並行架構與執行緒池技術（如 Spring ThreadPoolTaskExecutor、Java Virtual Threads 或 Quartz），
 *       達成應用層對底層執行緒環境與框架細節的零感知與純淨性。</li>
 * </ul>
 * </p>
 */
@FunctionalInterface
public interface FileTransmissionTaskSchedulerPort {

    /**
     * 派發非同步任務至背景執行緒執行
     *
     * @param task 欲於背景執行的工作內容
     */
    void schedule(Runnable task);
}
