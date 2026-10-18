package com.example.demo.application.shared.vo;

import java.io.Serializable;

/**
 * 是否生效狀態數值物件 (EffectiveStatus - Value Object / Enum)
 * <p>
 * <b>架構與業務職責說明 (AGENTS.md 1 與 2)：</b>
 * <ul>
 *   <li>位於應用層共享數值物件套件 <code>application/shared/vo/</code>。</li>
 *   <li><b>純 Java 領域數值物件 (Pure Java Value Object)：</b> 零框架與零外層依賴，封裝生效/失效業務邏輯與規則。</li>
 *   <li><b>資料庫落盤規範 (Database Mapping)：</b> 在關聯式資料庫 (MSSQL / H2) 中以 <code>"Y"</code> (生效) 或 <code>"N"</code> (未生效/失效/停用) 欄位儲存。</li>
 *   <li>提供豐富的型別轉換與判斷方法，支援與 Java <code>boolean</code>、資料庫代碼 (<code>"Y"</code>/<code>"N"</code>) 進行雙向安全轉換。</li>
 * </ul>
 * </p>
 */
public enum EffectiveStatus implements Serializable {

    /**
     * 生效 (Y)
     */
    EFFECTIVE("Y", true, "生效"),

    /**
     * 未生效 / 失效 / 停用 (N)
     */
    INEFFECTIVE("N", false, "未生效");

    /**
     * 別名常量：支援以 Y 直接引用生效狀態
     */
    public static final EffectiveStatus Y = EFFECTIVE;

    /**
     * 別名常量：支援以 N 直接引用未生效狀態
     */
    public static final EffectiveStatus N = INEFFECTIVE;

    private final String code;
    private final boolean effective;
    private final String description;

    EffectiveStatus(String code, boolean effective, String description) {
        this.code = code;
        this.effective = effective;
        this.description = description;
    }

    /**
     * 取得資料庫存儲之字串代碼 ("Y" 或 "N")
     *
     * @return "Y" 或 "N"
     */
    public String getCode() {
        return code;
    }

    /**
     * 判斷是否處於生效狀態
     *
     * @return 若為生效 (Y) 回傳 true，否則回傳 false
     */
    public boolean isEffective() {
        return effective;
    }

    /**
     * 判斷是否處於啟用狀態 (相容 isActive 語意)
     *
     * @return 若為生效/啟用回傳 true，否則回傳 false
     */
    public boolean isActive() {
        return effective;
    }

    /**
     * 轉換為 Java 原生 boolean 值
     *
     * @return true 代表生效，false 代表未生效
     */
    public boolean toBoolean() {
        return effective;
    }

    /**
     * 取得狀態中文描述說明
     *
     * @return "生效" 或 "未生效"
     */
    public String getDescription() {
        return description;
    }

    /**
     * 依資料庫代碼或文字解析為 EffectiveStatus
     * <p>支援 "Y", "N" (不分大小寫)，並具備相容性解析 "1", "0", "TRUE", "FALSE"。</p>
     *
     * @param code 代碼字串
     * @return 對應之 EffectiveStatus 列舉實例
     * @throws IllegalArgumentException 當傳入無法識別之代碼時拋出
     */
    public static EffectiveStatus fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return INEFFECTIVE;
        }
        String trimmed = code.trim().toUpperCase();
        if ("Y".equals(trimmed) || "YES".equals(trimmed) || "TRUE".equals(trimmed) || "1".equals(trimmed)) {
            return EFFECTIVE;
        }
        if ("N".equals(trimmed) || "NO".equals(trimmed) || "FALSE".equals(trimmed) || "0".equals(trimmed)) {
            return INEFFECTIVE;
        }
        throw new IllegalArgumentException("未知的生效狀態代碼: [" + code + "] (僅支援 'Y' 或 'N')");
    }

    /**
     * 依布林值轉換為 EffectiveStatus
     *
     * @param flag 布林值旗標 (若為 null 預設視為 INEFFECTIVE)
     * @return true 轉為 EFFECTIVE("Y")，false 轉為 INEFFECTIVE("N")
     */
    public static EffectiveStatus fromBoolean(Boolean flag) {
        return (flag != null && flag) ? EFFECTIVE : INEFFECTIVE;
    }

    /**
     * 通用靜態解析方法
     *
     * @param codeOrFlag 字串代碼
     * @return 對應之 EffectiveStatus
     */
    public static EffectiveStatus of(String codeOrFlag) {
        return fromCode(codeOrFlag);
    }

    /**
     * 通用靜態解析方法
     *
     * @param flag 布林值
     * @return 對應之 EffectiveStatus
     */
    public static EffectiveStatus of(Boolean flag) {
        return fromBoolean(flag);
    }

    @Override
    public String toString() {
        return code;
    }
}
