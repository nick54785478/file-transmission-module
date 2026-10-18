package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.DeleteServerCredentialCommand;

/**
 * 刪除伺服器傳輸憑證使用案例 (DeleteServerCredentialUseCase - Inbound Port)
 * <p>
 * <b>六角形架構合約規範 (AGENTS.md 2 與 3-8)：</b>
 * 命名規範: <code>in : ... + UseCase</code>。
 * 位於應用層 <code>application/port/in/</code>，代表刪除憑證之使用案例合約。
 * </p>
 */
public interface DeleteServerCredentialUseCase {

    /**
     * 依唯一識別碼刪除伺服器憑證
     *
     * @param command 刪除命令
     */
    void deleteCredential(DeleteServerCredentialCommand command);
}
