package com.example.demo.application.port.in;

/**
 * 處理待重試傳輸補償任務使用案例介面 (ProcessCompensationUseCase - Inbound Port)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>in : ... + UseCase</code> (AGENTS.md 3-8)。</li>
 *   <li>六角形架構 Inbound Port，負責觸發 Saga 補償日誌 (Outbox) 中到期的清理任務。</li>
 * </ul>
 * </p>
 */
public interface ProcessCompensationUseCase {

    /**
     * 掃描並執行所有到期之補償任務
     *
     * @return 成功完成補償之任務總數
     */
    int processPendingCompensations();
}
