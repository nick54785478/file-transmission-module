package com.example.demo.application.port.out;

import com.example.demo.application.shared.vo.TransmissionServerConfig;

/**
 * 憑證機密加解密輸出埠 (CredentialCipherPort - Outbound Port)
 * <p>
 * <b>架構與設計職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>out : ... + Port</code> (AGENTS.md 3-8)。</li>
 *   <li>位於 <code>application/port/out/</code>，代表應用層對外要求基礎設施提供的機密加解密技術合約。</li>
 *   <li>隔離密碼學實作細節 (如 AES-GCM 演算法、IV 產生、Tag 檢驗等)，提供純粹的抽象契約。</li>
 *   <li>傳參與回傳型別嚴格使用純領域物件 ({@link TransmissionServerConfig}) 與標準 Java 型別，嚴禁技術類物件洩入。</li>
 * </ul>
 * </p>
 */
public interface CredentialCipherPort {

    /**
     * 將明文字串以對稱演算法加密為 ENC(...) 格式密文
     *
     * @param plainText 待加密之明文字串
     * @return 包含 ENC(...) 標記之 Base64 密文字串；若輸入為空則回傳原值
     */
    String encrypt(String plainText);

    /**
     * 檢驗並解密字串：若符合 ENC(...) 密文標記則予以還原為明文；若為一般明文則原樣回傳
     *
     * @param text 待檢驗或解密之字串
     * @return 解密後之明文字串
     * @throws IllegalArgumentException 當密文格式毀損或金鑰不符時拋出
     */
    String decryptIfEncrypted(String text);

    /**
     * 判定指定字串是否為 ENC(...) 格式密文
     *
     * @param text 待檢驗字串
     * @return 若為已加密密文則回傳 true，否則回傳 false
     */
    boolean isEncrypted(String text);

    /**
     * 對傳輸伺服器配置中的機密憑證 (登入密碼與 SSH 私鑰 Passphrase) 進行解密
     *
     * @param serverConfig 原始伺服器連線配置數值物件
     * @return 憑證已解密還原之全新不可變 {@link TransmissionServerConfig} 物件
     */
    TransmissionServerConfig decryptServerConfig(TransmissionServerConfig serverConfig);

    /**
     * 空實作透傳常數 (No-op / Pass-through)：用於單元測試無需解密情境快速裝配，免去額外 Mock
     */
    CredentialCipherPort NOOP = new CredentialCipherPort() {
        @Override
        public String encrypt(String plainText) {
            return plainText;
        }

        @Override
        public String decryptIfEncrypted(String text) {
            return text;
        }

        @Override
        public boolean isEncrypted(String text) {
            return false;
        }

        @Override
        public TransmissionServerConfig decryptServerConfig(TransmissionServerConfig serverConfig) {
            return serverConfig;
        }
    };
}
