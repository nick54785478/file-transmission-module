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
 * @param keyword 搜尋關鍵字 (選填，若為空則不限關鍵字)
 * @param active 啟用狀態過濾 (選填，true=僅查詢有效憑證, false=僅查詢停用/軟刪除憑證, null=全量查詢)
 */
public record ListServerCredentialsQuery(
        String keyword,
        Boolean active
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 向下相容建構子：僅傳入 keyword
     *
     * @param keyword 搜尋關鍵字
     */
    public ListServerCredentialsQuery(String keyword) {
        this(keyword, null);
    }

    public static ListServerCredentialsQuery all() {
        return new ListServerCredentialsQuery(null, null);
    }

    public static ListServerCredentialsQuery withKeyword(String keyword) {
        return new ListServerCredentialsQuery(keyword, null);
    }

    public static ListServerCredentialsQuery of(String keyword, Boolean active) {
        return new ListServerCredentialsQuery(keyword, active);
    }
}
