package com.example.demo.application.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 傳輸機密加密與解密核心密碼學工具元件 (TransmissionCipher - Pure Java Cryptographic Engine)
 * <p>
 * <b>架構與設計職責說明：</b>
 * <ul>
 *   <li>位於 <code>application/shared/security/</code>，提供純 Java 標準高強度對稱加解密演算法 (AES-GCM 128-bit / AEAD)。</li>
 *   <li><b>零技術框架與零基礎設施依賴：</b> 僅依賴 JDK 內建安全套件 (<code>javax.crypto</code>, <code>java.security</code>)，嚴守 Clean Architecture 原則。</li>
 *   <li><b>密文標記化 (Encrypted Tokenization)：</b> 支援將敏感字串轉換為 <code>ENC(...)</code> 密文格式。</li>
 *   <li><b>隨機 IV 與防竄改標籤：</b> 每次加密均透過 {@link SecureRandom} 動態生成 12-byte IV，自帶 128-bit 驗證標籤。</li>
 *   <li><b>金鑰衍生機制 (Key Derivation)：</b> 透過 SHA-256 將任意長度密鑰均勻衍生為 128-bit (16 bytes) 之 AES 金鑰。</li>
 * </ul>
 * </p>
 */
public final class TransmissionCipher {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";

    /**
     * 開發與測試預備金鑰
     */
    public static final String DEFAULT_DEV_KEY = "FileTxModule@2026SecKey!";

    private static volatile byte[] defaultKeyBytes = deriveKey(DEFAULT_DEV_KEY);

    private TransmissionCipher() {
    }

    /**
     * 設定靜態門面所使用的全域自訂密鑰 (主要供離線工具或單元測試使用)
     *
     * @param secretKey 自訂密鑰字串
     */
    public static void setSecretKey(String secretKey) {
        String key = (secretKey != null && !secretKey.trim().isEmpty()) ? secretKey.trim() : DEFAULT_DEV_KEY;
        defaultKeyBytes = deriveKey(key);
    }

    /**
     * 重設靜態門面金鑰為預設備援金鑰
     */
    public static void resetKey() {
        defaultKeyBytes = deriveKey(DEFAULT_DEV_KEY);
    }

    /**
     * 使用預設/全域金鑰將明文字串以 AES-GCM 加密為 ENC(...) 格式
     *
     * @param plainText 待加密之明文字串
     * @return 包含 ENC(...) 標記之 Base64 密文
     */
    public static String encrypt(String plainText) {
        return encryptWithKey(plainText, defaultKeyBytes);
    }

    /**
     * 使用指定之金鑰位元組將明文字串以 AES-GCM 加密為 ENC(...) 格式
     *
     * @param plainText 待加密之明文字串
     * @param keyBytes 16-byte AES 金鑰
     * @return 包含 ENC(...) 標記之 Base64 密文
     */
    public static String encryptWithKey(String plainText, byte[] keyBytes) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);

            return ENC_PREFIX + Base64.getEncoder().encodeToString(combined) + ENC_SUFFIX;
        } catch (Exception ex) {
            throw new IllegalStateException("機密憑證加密失敗: " + ex.getMessage(), ex);
        }
    }

    /**
     * 使用預設/全域金鑰檢測並解密字串
     *
     * @param text 待檢驗或解密之字串
     * @return 解密後之明文字串
     */
    public static String decryptIfEncrypted(String text) {
        return decryptWithKey(text, defaultKeyBytes);
    }

    /**
     * 使用指定之金鑰位元組檢測並解密字串
     *
     * @param text 待檢驗或解密之字串
     * @param keyBytes 16-byte AES 金鑰
     * @return 解密後之明文字串
     * @throws IllegalArgumentException 當密文格式毀損或金鑰不符時拋出
     */
    public static String decryptWithKey(String text, byte[] keyBytes) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String trimmed = text.trim();
        if (!trimmed.startsWith(ENC_PREFIX) || !trimmed.endsWith(ENC_SUFFIX)) {
            return text;
        }

        String base64Payload = trimmed.substring(ENC_PREFIX.length(), trimmed.length() - ENC_SUFFIX.length()).trim();
        try {
            byte[] combined = Base64.getDecoder().decode(base64Payload);
            if (combined.length <= GCM_IV_LENGTH) {
                throw new IllegalArgumentException("無效的密文長度");
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

            int cipherLength = combined.length - GCM_IV_LENGTH;
            byte[] cipherBytes = new byte[cipherLength];
            System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherLength);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("機密密碼解密失敗 (密文毀損或金鑰不符): " + ex.getMessage(), ex);
        }
    }

    /**
     * 判定指定字串是否為 ENC(...) 格式密文
     *
     * @param text 待檢驗字串
     * @return 若為已加密密文則回傳 true，否則回傳 false
     */
    public static boolean isEncrypted(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.startsWith(ENC_PREFIX) && trimmed.endsWith(ENC_SUFFIX);
    }

    /**
     * 透過 SHA-256 將任意長度之密鑰字串衍生為安全的 128-bit (16 bytes) AES 金鑰
     *
     * @param keyString 原始密鑰字串
     * @return 16 bytes 金鑰位元組
     */
    public static byte[] deriveKey(String keyString) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(keyString.getBytes(StandardCharsets.UTF_8));
            byte[] key16 = new byte[16];
            System.arraycopy(hash, 0, key16, 0, 16);
            return key16;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("無法初始化 SHA-256 演算法", e);
        }
    }
}
