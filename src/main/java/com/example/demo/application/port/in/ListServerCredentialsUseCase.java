package com.example.demo.application.port.in;

import com.example.demo.application.shared.dto.ServerCredentialGottenResult;
import com.example.demo.application.shared.query.ListServerCredentialsQuery;

import java.util.List;

/**
 * 列出/搜尋伺服器傳輸憑證使用案例 (ListServerCredentialsUseCase - Inbound Port)
 * <p>
 * <b>六角形架構合約規範 (AGENTS.md 2 與 3-8)：</b>
 * 命名規範: <code>in : ... + UseCase</code>。
 * 位於應用層 <code>application/port/in/</code>，代表列出或依關鍵字搜尋憑證合約。
 * </p>
 */
public interface ListServerCredentialsUseCase {

    /**
     * 列出或模糊搜尋伺服器憑證列表
     *
     * @param query 查詢條件物件 (可含關鍵字)
     * @return 遮罩後之憑證查詢結果 DTO 列表
     */
    List<ServerCredentialGottenResult> listCredentials(ListServerCredentialsQuery query);
}
