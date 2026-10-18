package com.example.demo.infrastructure.persistence.credential.converter;

import com.example.demo.application.shared.vo.EffectiveStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 是否生效狀態 JPA 屬性轉換器單元測試 (EffectiveStatusConverterTest)
 * <p>
 * 遵循 AGENTS.md 測試規範，採用 BDD 與 Gherkin 語法驗證 JPA 實體欄位於資料庫 'Y'/'N' 的雙向落盤轉換。
 * </p>
 */
class EffectiveStatusConverterTest {

    private EffectiveStatusConverter converter;

    @BeforeEach
    void setUp() {
        converter = new EffectiveStatusConverter();
    }

    /**
     * Feature: 生效狀態 JPA 屬性落盤轉換
     * Scenario: 將 EffectiveStatus 轉換為資料庫欄位字串 'Y' 與 'N'
     * Given: 傳入 EffectiveStatus.EFFECTIVE, EffectiveStatus.INEFFECTIVE 與 null
     * When: 呼叫 convertToDatabaseColumn
     * Then: 分別轉換為 "Y", "N", "N"
     */
    @Test
    @DisplayName("情境：實體轉換為資料庫欄位時正確產生 'Y' 與 'N'")
    void testConvertToDatabaseColumn() {
        assertThat(converter.convertToDatabaseColumn(EffectiveStatus.EFFECTIVE)).isEqualTo("Y");
        assertThat(converter.convertToDatabaseColumn(EffectiveStatus.INEFFECTIVE)).isEqualTo("N");
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("N");
    }

    /**
     * Feature: 生效狀態 JPA 屬性出庫還原轉換
     * Scenario: 將資料庫欄位字串 'Y' 與 'N' 還原為 EffectiveStatus
     * Given: 傳入 "Y", "N", "y", "n", null
     * When: 呼叫 convertToEntityAttribute
     * Then: 正確還原為 EffectiveStatus.EFFECTIVE 或 EffectiveStatus.INEFFECTIVE
     */
    @Test
    @DisplayName("情境：資料庫欄位 'Y' 與 'N' 正確還原為 EffectiveStatus 實例")
    void testConvertToEntityAttribute() {
        assertThat(converter.convertToEntityAttribute("Y")).isEqualTo(EffectiveStatus.EFFECTIVE);
        assertThat(converter.convertToEntityAttribute("y")).isEqualTo(EffectiveStatus.EFFECTIVE);
        assertThat(converter.convertToEntityAttribute("N")).isEqualTo(EffectiveStatus.INEFFECTIVE);
        assertThat(converter.convertToEntityAttribute("n")).isEqualTo(EffectiveStatus.INEFFECTIVE);
        assertThat(converter.convertToEntityAttribute(null)).isEqualTo(EffectiveStatus.INEFFECTIVE);
    }
}
