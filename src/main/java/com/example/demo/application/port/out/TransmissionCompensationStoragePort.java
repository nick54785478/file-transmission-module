package com.example.demo.application.port.out;

import com.example.demo.application.shared.vo.CompensationTask;
import java.time.Instant;
import java.util.List;

/**
 * 傳輸補償任務儲存輸出埠介面 (TransmissionCompensationStoragePort - Outbound Port)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>out : ... + Port</code> (AGENTS.md 3-8)。</li>
 *   <li>六角形架構 Outbound Port，定義 Saga 補償交易日誌 (Outbox) 的持久化與查詢合約。</li>
 *   <li>傳遞參數與回傳值僅使用純粹值物件 {@link CompensationTask}，絕不洩漏 JPA 實體細節。</li>
 * </ul>
 * </p>
 */
public interface TransmissionCompensationStoragePort {

    /**
     * 儲存待重試之補償任務
     *
     * @param task 領域補償任務物件
     * @return 包含持久化主鍵 ID 之補償任務物件
     */
    CompensationTask saveCompensation(CompensationTask task);

    /**
     * 查詢所有到期待重試之補償任務
     *
     * @param now 目前時間戳記 (UTC Instant)
     * @return 待執行之補償任務清單
     */
    List<CompensationTask> findPendingRetries(Instant now);

    /**
     * 更新現有補償任務執行狀態
     *
     * @param task 待更新之補償任務物件
     */
    void updateCompensation(CompensationTask task);
}
