package com.example.demo.application.shared.query;

import java.io.Serializable;

/**
 * 列出/搜尋伺服器憑證查詢物件 (ListServerCredentialsQuery - Query)
 * <p>
 * <b>架構與命名規範 (AGENTS.md 2 與 3-5)：</b>
 * 命名規範: <code>V + N + Query</code>。
 * 位於應用層 <code>application/shared/query/</code>，支援關鍵字模糊搜尋或全量列出。
 * </p>
 *
 * @param keyword 搜尋關鍵字 (選填，若為空則列出全部)
 */
public record ListServerCredentialsQuery(
        String keyword
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static ListServerCredentialsQuery all() {
        return new ListServerCredentialsQuery(null);
    }

    public static ListServerCredentialsQuery withKeyword(String keyword) {
        return new ListServerCredentialsQuery(keyword);
    }
}
