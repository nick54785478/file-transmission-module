package com.example.demo.application.shared.dto;

import java.io.Serializable;

/**
 * 傳輸對帳自癒作業執行結果資料傳輸物件 (TransmissionReconciliationGottenResult - DTO Record)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>N + Gotten + Result</code> (AGENTS.md 3-6)。</li>
 *   <li>純資料載體 (Pure Data Carrier)，禁止在內部實作轉換邏輯。</li>
 *   <li>封裝對帳批次執行成果統計：掃描總數、自癒成功數、過期清理數與失敗數。</li>
 * </ul>
 * </p>
 *
 * @param totalScanned 掃描發現之超時殭屍任務總數
 * @param selfHealedCount 遠端檔案已完整存在並自動自癒為成功之任務數
 * @param purgedCount 殘存臨時檔已被成功清理並標記失敗之任務數
 * @param failedCount 探測失敗或無臨時檔可清理直接標記失敗之任務數
 */
public record TransmissionReconciliationGottenResult(
        int totalScanned,
        int selfHealedCount,
        int purgedCount,
        int failedCount
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 便捷工廠方法：建立空的對帳結果
     *
     * @return 空結果物件 (所有統計均為 0)
     */
    public static TransmissionReconciliationGottenResult empty() {
        return new TransmissionReconciliationGottenResult(0, 0, 0, 0);
    }
}
