package com.example.demo.config.properties;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 檔案傳輸機密安全性配置屬性載體 (TransmissionSecurityProperties - Configuration Layer)
 * <p>
 * <b>架構與設計職責說明：</b>
 * <ul>
 *   <li>位於專案根目錄下之全域配置層 (config/properties/ 套件)，遵循 AGENTS.md 集中存放架構規範。</li>
 *   <li>透過 <code>@ConfigurationProperties(prefix = "transmission.security")</code> 綁定 <code>application.properties</code> 中的配置項。</li>
 *   <li>提供型別安全的配置物件，支援環境變數、外部設定檔或雲端配置中心注入加解密金鑰。</li>
 *   <li>預設提供開發測試用後備金鑰，若生產環境未覆寫，將由配置層發出資安警告。</li>
 * </ul>
 * </p>
 */
@Getter
@ConfigurationProperties(prefix = "transmission.security")
public class TransmissionSecurityProperties {

    /**
     * 預設開發與測試備援金鑰
     */
    public static final String DEFAULT_DEV_KEY = "FileTxModule@2026SecKey!";

    /**
     * AES-GCM 傳輸機密加密金鑰 (支援任意長度，底層透過 SHA-256 自動衍生為 128-bit 金鑰)
     * -- GETTER --
     *  取得目前配置之加密金鑰
     *
     * @return 金鑰字串
     */
    private String secretKey = DEFAULT_DEV_KEY;

    /**
     * 無參建構子
     */
    public TransmissionSecurityProperties() {
    }

    /**
     * 建構子：指定自訂金鑰
     *
     * @param secretKey 自訂金鑰
     */
    public TransmissionSecurityProperties(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * 設定加密金鑰
     *
     * @param secretKey 金鑰字串
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * 判定是否使用了預設的開發測試金鑰
     *
     * @return 若為預設金鑰或為空則回傳 true
     */
    public boolean isDefaultOrEmpty() {
        return secretKey == null || secretKey.trim().isEmpty() || DEFAULT_DEV_KEY.equals(secretKey.trim());
    }
}
