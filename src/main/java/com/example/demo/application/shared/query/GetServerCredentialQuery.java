package com.example.demo.application.shared.query;

import java.io.Serializable;

/**
 * 查詢單一伺服器憑證查詢物件 (GetServerCredentialQuery - Query)
 * <p>
 * <b>架構與命名規範 (AGENTS.md 2 與 3-5)：</b>
 * 命名規範: <code>V + N + Query</code>。
 * 位於應用層 <code>application/shared/query/</code>，支援透過 ID 或 Alias 進行精確查詢。
 * </p>
 *
 * @param id 憑證唯一識別碼 (選填)
 * @param alias 憑證易讀別名 (選填)
 */
public record GetServerCredentialQuery(
        String id,
        String alias
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static GetServerCredentialQuery byId(String id) {
        return new GetServerCredentialQuery(id, null);
    }

    public static GetServerCredentialQuery byAlias(String alias) {
        return new GetServerCredentialQuery(null, alias);
    }
}
