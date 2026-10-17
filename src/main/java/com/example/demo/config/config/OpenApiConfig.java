package com.example.demo.config.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 與 Swagger 全域配置類 (OpenApiConfig - Configuration Layer)
 * <p>
 * <b>架構與設計職責說明：</b>
 * <ul>
 *   <li>位於專案根目錄下之全域配置層 (config/ 套件)，遵循 AGENTS.md 集中存放架構規範。</li>
 *   <li>定義檔案傳輸模組 (File Transmission Module) 之 API 核心文件元數據 (Title, Version, Description)。</li>
 *   <li>整合 SpringDoc OpenAPI 3，為表現層 REST API 控制器自動生成結構化 OpenAPI 文件與互動式 Swagger UI 介面。</li>
 * </ul>
 * </p>
 */
@Configuration
public class OpenApiConfig {

    /**
     * 配置 OpenAPI 規格實體
     *
     * @return 包含模組名稱、版本與說明的 OpenAPI 規格物件
     */
    @Bean
    public OpenAPI fileTransmissionOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("檔案傳輸模組 REST API (File Transmission Module)")
                        .version("1.0.0")
                        .description("本模組基於六角形架構 (Hexagonal Architecture) 實作，支援 FTP、FTPS、SFTP 協定之同步檔案上傳/下載、非同步長任務提交 (202 Accepted)、目錄雙向同步與作業狀態追蹤查詢。")
                        .contact(new Contact()
                                .name("檔案傳輸模組核心團隊")
                                .email("module-support@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
