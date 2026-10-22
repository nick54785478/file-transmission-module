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

    /**
     * 支援以狀態字串代碼 ("Y" / "N", "true" / "false") 建構查詢物件
     *
     * @param keyword      搜尋關鍵字 (選填)
     * @param activeStatus 狀態代碼字串 ("Y", "N", "true", "false", 選填)
     * @return 查詢物件 ListServerCredentialsQuery
     */
    public static ListServerCredentialsQuery ofStatus(String keyword, String activeStatus) {
        return new ListServerCredentialsQuery(keyword, parseActive(activeStatus));
    }

    /**
     * 安全解析啟用狀態字串 (支援 Y / N, true / false, 1 / 0)
     *
     * @param statusStr 狀態代碼字串
     * @return 解析後之 Boolean，若輸入為 null 或空白字串則回傳 null (代表全量不限狀態)
     * @throws IllegalArgumentException 當輸入無法識別之狀態字串時拋出
     */
    public static Boolean parseActive(String statusStr) {
        if (statusStr == null || statusStr.trim().isEmpty()) {
            return null;
        }
        String trimmed = statusStr.trim().toUpperCase();
        if ("Y".equals(trimmed) || "TRUE".equals(trimmed) || "1".equals(trimmed) || "YES".equals(trimmed)) {
            return Boolean.TRUE;
        }
        if ("N".equals(trimmed) || "FALSE".equals(trimmed) || "0".equals(trimmed) || "NO".equals(trimmed)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("未知的生效狀態代碼: [" + statusStr + "] (僅支援 'Y' 或 'N')");
    }
}
