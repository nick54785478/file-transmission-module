package com.example.demo.application.shared.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * 伺服器傳輸憑證查詢結果資料傳遞載體 (ServerCredentialGottenResult - DTO)
 * <p>
 * <b>架構與命名規範 (AGENTS.md 2 與 3-6)：</b>
 * <ul>
 *   <li>命名規範: <code>N + Gotten + Result</code>。</li>
 *   <li>位於應用層 <code>application/shared/dto/</code>。</li>
 *   <li><b>純資料載體 (Pure Data Carrier)：</b> 以不可變 Java Record 實作，嚴格禁止包含領域業務轉換邏輯。</li>
 *   <li><b>資安防護設計：</b> 密碼、私鑰與 Passphrase 均已進行脫敏遮罩處理，杜絕機密於網路傳輸或日誌中洩漏。</li>
 * </ul>
 * </p>
 *
 * @param id 憑證唯一識別碼
 * @param alias 憑證易讀別名 (如 mock-sftp)
 * @param host 伺服器主機名稱或 IP
 * @param port 通訊埠號
 * @param protocol 傳輸協定 (SFTP / FTP / FTPS)
 * @param username 登入使用者帳號
 * @param maskedPassword 遮罩後密碼 (如 ****** 或 null)
 * @param maskedPrivateKey 遮罩後私鑰標記 (如 [SSH_PRIVATE_KEY] 或 null)
 * @param maskedPassphrase 遮罩後私鑰密碼 (如 ****** 或 null)
 * @param hasPassword 是否已配置登入密碼
 * @param hasPrivateKey 是否已配置 SSH 私鑰
 * @param hasPassphrase 是否已配置私鑰保護密碼
 * @param active 是否處於啟用狀態
 * @param description 備註說明
 * @param createdAt 建立時間戳記
 * @param updatedAt 最後更新時間戳記
 */
public record ServerCredentialGottenResult(
        String id,
        String alias,
        String host,
        Integer port,
        String protocol,
        String username,
        String maskedPassword,
        String maskedPrivateKey,
        String maskedPassphrase,
        boolean hasPassword,
        boolean hasPrivateKey,
        boolean hasPassphrase,
        boolean active,
        String description,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
