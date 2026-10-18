package com.example.demo.application.shared.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 是否生效狀態數值物件單元測試 (EffectiveStatusTest)
 * <p>
 * 遵循 AGENTS.md 測試規範，採用 BDD 與 Gherkin 語法驗證 'Y'/'N' 代碼解析與布林轉換邏輯。
 * </p>
 */
class EffectiveStatusTest {

    /**
     * Feature: 生效狀態數值物件代碼映射
     * Scenario: 驗證代碼為 'Y' 或正向字串時成功解析為 EFFECTIVE
     * Given: 傳入 "Y", "y", "YES", "TRUE", "1"
     * When: 呼叫 EffectiveStatus.fromCode 方法
     * Then: 回傳 EFFECTIVE，且 isEffective() 為 true，getCode() 為 "Y"
     */
    @ParameterizedTest
    @ValueSource(strings = {"Y", "y", "YES", "yes", "TRUE", "true", "1"})
    @DisplayName("情境：正向代碼正確解析為 EFFECTIVE (Y)")
    void testFromCodeEffective(String code) {
        EffectiveStatus status = EffectiveStatus.fromCode(code);

        assertThat(status).isEqualTo(EffectiveStatus.EFFECTIVE);
        assertThat(status.isEffective()).isTrue();
        assertThat(status.isActive()).isTrue();
        assertThat(status.toBoolean()).isTrue();
        assertThat(status.getCode()).isEqualTo("Y");
    }

    /**
     * Feature: 生效狀態數值物件代碼映射
     * Scenario: 驗證代碼為 'N' 或負向字串或空值時成功解析為 INEFFECTIVE
     * Given: 傳入 "N", "n", "NO", "FALSE", "0", ""
     * When: 呼叫 EffectiveStatus.fromCode 方法
     * Then: 回傳 INEFFECTIVE，且 isEffective() 為 false，getCode() 為 "N"
     */
    @ParameterizedTest
    @ValueSource(strings = {"N", "n", "NO", "no", "FALSE", "false", "0"})
    @DisplayName("情境：負向代碼正確解析為 INEFFECTIVE (N)")
    void testFromCodeIneffective(String code) {
        EffectiveStatus status = EffectiveStatus.fromCode(code);

        assertThat(status).isEqualTo(EffectiveStatus.INEFFECTIVE);
        assertThat(status.isEffective()).isFalse();
        assertThat(status.isActive()).isFalse();
        assertThat(status.toBoolean()).isFalse();
        assertThat(status.getCode()).isEqualTo("N");
    }

    /**
     * Feature: 生效狀態數值物件代碼映射
     * Scenario: 驗證代碼為 null 或空白時預設解析為 INEFFECTIVE
     * Given: 傳入 null 或空白字串
     * When: 呼叫 EffectiveStatus.fromCode 方法
     * Then: 回傳 INEFFECTIVE
     */
    @Test
    @DisplayName("情境：null 或空白字串安全解析為 INEFFECTIVE")
    void testFromCodeNullOrEmptyReturnsIneffective() {
        assertThat(EffectiveStatus.fromCode(null)).isEqualTo(EffectiveStatus.INEFFECTIVE);
        assertThat(EffectiveStatus.fromCode("   ")).isEqualTo(EffectiveStatus.INEFFECTIVE);
    }

    /**
     * Feature: 生效狀態數值物件代碼映射
     * Scenario: 傳入非法未知代碼拋出 IllegalArgumentException
     * Given: 傳入 "UNKNOWN" 等未知代碼
     * When: 呼叫 EffectiveStatus.fromCode 方法
     * Then: 拋出 IllegalArgumentException
     */
    @Test
    @DisplayName("情境：未知代碼拋出 IllegalArgumentException")
    void testFromCodeUnknownThrowsException() {
        assertThatThrownBy(() -> EffectiveStatus.fromCode("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知的生效狀態代碼");
    }

    /**
     * Feature: 生效狀態數值物件布林轉換
     * Scenario: 驗證由 Boolean 進行雙向轉換
     * Given: 傳入 Boolean.TRUE, Boolean.FALSE, null
     * When: 呼叫 EffectiveStatus.fromBoolean 方法
     * Then: 正確轉換為對應之 EffectiveStatus 實例
     */
    @Test
    @DisplayName("情境：由 Boolean 正確轉換為 EffectiveStatus")
    void testFromBoolean() {
        assertThat(EffectiveStatus.fromBoolean(true)).isEqualTo(EffectiveStatus.EFFECTIVE);
        assertThat(EffectiveStatus.fromBoolean(false)).isEqualTo(EffectiveStatus.INEFFECTIVE);
        assertThat(EffectiveStatus.fromBoolean(null)).isEqualTo(EffectiveStatus.INEFFECTIVE);
    }

    /**
     * Feature: 生效狀態別名字段支援
     * Scenario: 驗證 Y 與 N 別名常量正確指向 EFFECTIVE 與 INEFFECTIVE
     * Given: 存取 EffectiveStatus.Y 與 EffectiveStatus.N
     * When: 比對列舉實例
     * Then: 分別與 EFFECTIVE 和 INEFFECTIVE 等價
     */
    @Test
    @DisplayName("情境：別名常量 Y 與 N 與 EFFECTIVE / INEFFECTIVE 等價")
    void testAliasConstants() {
        assertThat(EffectiveStatus.Y).isSameAs(EffectiveStatus.EFFECTIVE);
        assertThat(EffectiveStatus.N).isSameAs(EffectiveStatus.INEFFECTIVE);
    }
}
