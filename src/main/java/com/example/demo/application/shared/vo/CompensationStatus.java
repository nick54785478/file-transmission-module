package com.example.demo.application.shared.vo;

/**
 * 補償任務執行狀態列舉 (CompensationStatus - Value Object Enum)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>應用層共用數值物件 (Shared VO Enum)，記錄 Saga 補償日誌 (Outbox) 中任務的生命週期狀態。</li>
 *   <li>狀態流轉：PENDING -> RETRYING -> SUCCESS / ABANDONED。</li>
 * </ul>
 * </p>
 */
public enum CompensationStatus {

    /**
     * 待執行：初次寫入補償日誌，等待背景排程挑選執行
     */
    PENDING,

    /**
     * 重試中：曾執行失敗，依指數退避等待下一次重試
     */
    RETRYING,

    /**
     * 補償成功：遠端清理動作已圓滿完成
     */
    SUCCESS,

    /**
     * 補償放棄：超過最大重試次數上限，標記放棄並通知維運人員
     */
    ABANDONED
}
