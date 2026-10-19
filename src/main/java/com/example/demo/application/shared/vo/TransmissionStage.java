package com.example.demo.application.shared.vo;

/**
 * 檔案傳輸內部作業階段列舉 (TransmissionStage - Value Object Enum)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>應用層共用數值物件 (Shared VO Enum)，用於標記傳輸任務在 2PC 模擬中的細部生命週期狀態。</li>
 *   <li>支援階段包含：暫存串流寫入 (STAGING)、完整性檢核 (VERIFYING)、原子更名提交 (COMMITTING) 以及終態。</li>
 *   <li>協助背景對帳引擎 (Reconciliation Engine) 精確判斷中途中斷任務當前所處的技術階段。</li>
 * </ul>
 * </p>
 */
public enum TransmissionStage {

    /**
     * 暫存串流寫入階段：串流寫入遠端臨時檔 (.staging)
     */
    STAGING,

    /**
     * 完整性檢核階段：檢核暫存檔大小與存在性
     */
    VERIFYING,

    /**
     * 原子更名提交階段：發送協定 RENAME 指令原子切換為正式檔名
     */
    COMMITTING,

    /**
     * 補償回滾階段：傳輸失敗並執行反向清理中
     */
    COMPENSATING,

    /**
     * 作業已成功提交完成終態
     */
    COMPLETED,

    /**
     * 作業失敗終態
     */
    FAILED
}
