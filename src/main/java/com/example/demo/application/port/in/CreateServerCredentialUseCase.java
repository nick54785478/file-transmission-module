package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.CreateServerCredentialCommand;
import com.example.demo.application.shared.dto.ServerCredentialGottenResult;

/**
 * 建立伺服器傳輸憑證使用案例 (CreateServerCredentialUseCase - Inbound Port)
 * <p>
 * <b>六角形架構合約規範 (AGENTS.md 2 與 3-8)：</b>
 * 命名規範: <code>in : ... + UseCase</code>。
 * 位於應用層 <code>application/port/in/</code>，代表對外開放的業務使用案例合約。
 * </p>
 */
public interface CreateServerCredentialUseCase {

    /**
     * 建立全新伺服器憑證
     *
     * @param command 建立命令
     * @return 建立成功後之憑證查詢結果 DTO
     */
    ServerCredentialGottenResult createCredential(CreateServerCredentialCommand command);
}
