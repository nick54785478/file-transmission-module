package com.example.demo.application.shared.vo;

/**
 * 目錄同步傳輸方向列舉 (SyncDirection - Value Object / Enum)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>代表應用層與輸出埠 (FileTransmissionGatewayPort) 執行目錄同步時的傳輸流向數值物件。</li>
 *   <li>定義本機目錄與遠端主機目錄之間單向同步流向。</li>
 * </ul>
 * </p>
 */
public enum SyncDirection {

    /**
     * 從本機目錄同步至遠端主機 (Local -> Remote)
     */
    LOCAL_TO_REMOTE,

    /**
     * 從遠端主機同步至本機目錄 (Remote -> Local)
     */
    REMOTE_TO_LOCAL
}
