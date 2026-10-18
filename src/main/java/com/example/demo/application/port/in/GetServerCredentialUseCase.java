package com.example.demo.application.port.in;

import com.example.demo.application.shared.dto.ServerCredentialGottenResult;
import com.example.demo.application.shared.query.GetServerCredentialQuery;

/**
 * 查詢單一伺服器傳輸憑證使用案例 (GetServerCredentialUseCase - Inbound Port)
 * <p>
 * <b>六角形架構合約規範 (AGENTS.md 2 與 3-8)：</b>
 * 命名規範: <code>in : ... + UseCase</code>。
 * 位於應用層 <code>application/port/in/</code>，代表透過 ID 或別名查詢單一憑證合約。
 * </p>
 */
public interface GetServerCredentialUseCase {

    /**
     * 依查詢參數取得單一伺服器憑證
     *
     * @param query 查詢物件 (包含 id 或 alias)
     * @return 遮罩後之憑證查詢結果 DTO
     */
    ServerCredentialGottenResult getCredential(GetServerCredentialQuery query);
}
