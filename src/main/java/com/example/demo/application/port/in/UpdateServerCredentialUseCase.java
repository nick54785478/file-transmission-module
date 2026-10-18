package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.UpdateServerCredentialCommand;
import com.example.demo.application.shared.dto.ServerCredentialGottenResult;

/**
 * 更新伺服器傳輸憑證使用案例 (UpdateServerCredentialUseCase - Inbound Port)
 * <p>
 * <b>六角形架構合約規範 (AGENTS.md 2 與 3-8)：</b>
 * 命名規範: <code>in : ... + UseCase</code>。
 * 位於應用層 <code>application/port/in/</code>，代表更新既有憑證的使用案例合約。
 * </p>
 */
public interface UpdateServerCredentialUseCase {

    /**
     * 更新既有伺服器憑證
     *
     * @param command 更新命令
     * @return 更新成功後之憑證查詢結果 DTO
     */
    ServerCredentialGottenResult updateCredential(UpdateServerCredentialCommand command);
}
