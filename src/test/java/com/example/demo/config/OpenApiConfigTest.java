package com.example.demo.config;

import com.example.demo.config.config.OpenApiConfig;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * OpenAPI 配置類單元測試 (OpenApiConfigTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 驗證 OpenApiConfig 所生成之 OpenAPI 實體元數據設定正確；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
class OpenApiConfigTest {

    /**
     * Feature: OpenAPI 規格配置
     * Scenario: 驗證 OpenAPI Bean 的基本資訊設定
     * Given: 建立 OpenApiConfig 實例
     * When: 呼叫 fileTransmissionOpenAPI() 取得 OpenAPI 規格
     * Then: 應成功取得非空之 OpenAPI 實體，且 Title、Version 與 License 皆正確配置
     */
    @Test
    @DisplayName("情境：驗證 OpenAPI 規格之 Title, Version 與 License 配置正確")
    void shouldConfigureOpenApiProperly() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.fileTransmissionOpenAPI();

        assertNotNull(openAPI);
        assertNotNull(openAPI.getInfo());
        assertEquals("檔案傳輸模組 REST API (File Transmission Module)", openAPI.getInfo().getTitle());
        assertEquals("1.0.0", openAPI.getInfo().getVersion());
        assertNotNull(openAPI.getInfo().getLicense());
        assertEquals("Apache 2.0", openAPI.getInfo().getLicense().getName());
    }
}
