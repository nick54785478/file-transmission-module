package com.example.demo.infrastructure.adapter;

import com.example.demo.application.port.out.CredentialCipherPort;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 憑證機密加解密基礎設施適配器 (CredentialCipherAdapter - Outbound Adapter)
 * <p>
 * <b>架構與設計職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>out : ... + Adapter</code> (AGENTS.md 3-9)。</li>
 *   <li>位於 <code>infrastructure/adapter/</code>，實作應用層 Outbound Port ({@link CredentialCipherPort})。</li>
 *   <li><b>純實例化與零靜態狀態 (Zero Static State)：</b> 徹底擺脫靜態全域狀態，金鑰由建構子注入，生命週期交由 Spring 容器管理。</li>
 *   <li><b>AES-GCM (128-bit) 工業標準安全規格：</b> 每次加密均動態生成 12-byte 隨機 IV，自帶 128-bit AEAD 認證標籤防竄改。</li>
 *   <li><b>SHA-256 金鑰衍生 (Key Derivation)：</b> 將任意長度之密鑰字串均勻衍生為合規之 128-bit AES 金鑰。</li>
 * </ul>
 * </p>
 */
public class CredentialCipherAdapter implements CredentialCipherPort {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";

    private final byte[] secretKeyBytes;

    /**
     * 建構憑證加解密適配器
     *
     * @param rawSecretKey 外部注入之原始金鑰字串 (經由 SHA-256 自動衍生為 128-bit AES 金鑰)
     */
    public CredentialCipherAdapter(String rawSecretKey) {
        String key = (rawSecretKey != null && !rawSecretKey.trim().isEmpty())
                ? rawSecretKey.trim()
                : "FileTxModule@2026SecKey!";
        this.secretKeyBytes = deriveKey(key);
    }

    @Override
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            SecretKeySpec keySpec = new SecretKeySpec(this.secretKeyBytes, ALGORITHM);
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

    @Override
    public String decryptIfEncrypted(String text) {
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

            SecretKeySpec keySpec = new SecretKeySpec(this.secretKeyBytes, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("機密密碼解密失敗 (密文毀損或金鑰不符): " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean isEncrypted(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.startsWith(ENC_PREFIX) && trimmed.endsWith(ENC_SUFFIX);
    }

    @Override
    public TransmissionServerConfig decryptServerConfig(TransmissionServerConfig serverConfig) {
        if (serverConfig == null) {
            return null;
        }

        String decryptedPassword = decryptIfEncrypted(serverConfig.getPassword());
        String decryptedPrivateKey = (serverConfig.hasPrivateKey())
                ? decryptIfEncrypted(serverConfig.getPrivateKey())
                : null;
        String decryptedPassphrase = (serverConfig.hasPassphrase())
                ? decryptIfEncrypted(serverConfig.getPassphrase())
                : null;

        return serverConfig.withDecryptedCredentials(decryptedPassword, decryptedPrivateKey, decryptedPassphrase);
    }

    /**
     * 透過 SHA-256 將任意長度之密鑰字串衍生為安全的 128-bit (16 bytes) AES 金鑰
     *
     * @param keyString 原始密鑰字串
     * @return 16 bytes 金鑰位元組
     */
    private static byte[] deriveKey(String keyString) {
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
