package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.ReactivateServerCredentialCommand;
import com.example.demo.application.shared.dto.ServerCredentialGottenResult;

/**
 * 重新啟用/生效伺服器傳輸憑證使用案例 (ReactivateServerCredentialUseCase - Inbound Port)
 * <p>
 * <b>六角形架構合約規範 (AGENTS.md 2 與 3-8)：</b>
 * <ul>
 *   <li>命名規範: <code>in : ... + UseCase</code>。</li>
 *   <li>位於應用層 <code>application/port/in/</code>，代表重新啟用/生效停用或軟刪除憑證之業務合約。</li>
 *   <li><b>方法傳參及回傳型別嚴禁使用任何技術類或環境相依物件：</b> 僅接收 Command 並回傳 DTO。</li>
 * </ul>
 * </p>
 */
public interface ReactivateServerCredentialUseCase {

    /**
     * 依唯一識別碼或別名重新啟用/生效伺服器憑證
     *
     * @param command 重新啟用命令
     * @return 重新生效後之伺服器憑證查詢結果 DTO
     */
    ServerCredentialGottenResult reactivateCredential(ReactivateServerCredentialCommand command);
}
