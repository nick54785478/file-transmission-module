package com.example.demo.config.config;

import com.example.demo.application.port.out.CredentialCipherPort;
import com.example.demo.config.properties.TransmissionSecurityProperties;
import com.example.demo.infrastructure.adapter.CredentialCipherAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 檔案傳輸模組全域配置類 (FileTransmissionConfig - Configuration Layer)
 * <p>
 * 遵循架構準則 (AGENTS.md 第 2 點)：
 * 全域配置集中存放於根目錄下的 config 套件中。
 * 本類別為系統中處理技術框架裝配的核心配置，組裝 Domain、Application 與 Infrastructure 基礎設施層。
 * 負責透過 {@link TransmissionSecurityProperties} 讀取外部配置，並向 Spring 容器註冊 {@link CredentialCipherPort} 介面之實例 Bean。
 * </p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(TransmissionSecurityProperties.class)
public class FileTransmissionConfig {

    /**
     * 向 Spring 容器註冊憑證機密加解密輸出埠 (CredentialCipherPort) Bean
     *
     * @param securityProperties 傳輸安全性配置屬性載體
     * @return 憑證加解密適配器實例
     */
    @Bean
    public CredentialCipherPort credentialCipherPort(TransmissionSecurityProperties securityProperties) {
        String key = securityProperties.getSecretKey();
        if (securityProperties.isDefaultOrEmpty()) {
            log.warn("[Security] 未檢測到自訂傳輸金鑰設定，目前使用開發用預設金鑰！生產環境強烈建議於 application.properties 或環境變數配置 transmission.security.secret-key");
        } else {
            log.info("[Security] 已成功透過 TransmissionSecurityProperties 載入外部自訂加密金鑰");
        }
        return new CredentialCipherAdapter(key);
    }
}
