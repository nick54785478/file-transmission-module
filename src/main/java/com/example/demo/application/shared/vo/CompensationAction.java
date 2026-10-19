package com.example.demo.application.shared.vo;

/**
 * 補償交易行為類型列舉 (CompensationAction - Value Object Enum)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>應用層共用數值物件 (Shared VO Enum)，定義 Saga 補償交易中的具體反向回滾動作。</li>
 *   <li>支援類型：
 *     <ul>
 *       <li><code>DELETE_REMOTE_STAGING</code>：刪除遠端遺留的 .staging 暫存檔。</li>
 *       <li><code>RENAME_BACK</code>：更名回滾 (如還原備份的 .bak 檔案)。</li>
 *       <li><code>DELETE_LOCAL_ORPHAN</code>：刪除本機殘缺孤兒檔。</li>
 *     </ul>
 *   </li>
 * </ul>
 * </p>
 */
public enum CompensationAction {

    /**
     * 刪除遠端遺留之 .staging 暫存檔
     */
    DELETE_REMOTE_STAGING,

    /**
     * 還原既有備份檔案更名
     */
    RENAME_BACK,

    /**
     * 刪除本機殘留之未完整下載檔案
     */
    DELETE_LOCAL_ORPHAN
}
