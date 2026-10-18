package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.DeleteServerCredentialCommand;

/**
 * 軟刪除伺服器傳輸憑證使用案例 (DeleteServerCredentialUseCase - Inbound Port)
 * <p>
 * <b>六角形架構合約規範 (AGENTS.md 2 與 3-8)：</b>
 * <ul>
 *   <li>命名規範: <code>in : ... + UseCase</code>。</li>
 *   <li>位於應用層 <code>application/port/in/</code>，代表軟刪除憑證之業務合約。</li>
 *   <li><b>業務語意：</b> 將指定連線憑證標記為停用/失效狀態 (軟刪除)，保留資料庫落盤歷史紀錄並禁止後續傳輸作業引用。</li>
 * </ul>
 * </p>
 */
public interface DeleteServerCredentialUseCase {

    /**
     * 依唯一識別碼或別名軟刪除伺服器憑證 (標記為停用/失效)
     *
     * @param command 刪除命令 (包含憑證唯一識別碼或別名)
     */
    void deleteCredential(DeleteServerCredentialCommand command);
}
