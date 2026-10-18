package com.example.demo.presentation.resource.out;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.Instant;

/**
 * 伺服器傳輸憑證回應資料載體 (ServerCredentialRetrievedResource - Response Resource)
 * <p>
 * <b>架構與命名規範 (AGENTS.md 2 與 3-2)：</b>
 * <ul>
 *   <li>命名規範: <code>N + Ved + Resource</code>。</li>
 *   <li>位於表現層 <code>presentation/resource/out/</code>。</li>
 *   <li><b>純資料載體 (Pure Data Carrier)：</b> 密碼與私鑰等機密資料一律以遮罩狀態呈現，嚴格確保資訊安全。</li>
 * </ul>
 * </p>
 */
@Schema(description = "伺服器傳輸憑證資訊回應資料")
public record ServerCredentialRetrievedResource(
        @Schema(description = "憑證唯一識別碼", example = "550e8400-e29b-41d4-a716-446655440000")
        String id,

        @Schema(description = "憑證易讀別名", example = "mock-sftp")
        String alias,

        @Schema(description = "伺服器主機名稱或 IP", example = "127.0.0.1")
        String host,

        @Schema(description = "伺服器通訊埠", example = "2222")
        Integer port,

        @Schema(description = "傳輸協定", example = "SFTP")
        String protocol,

        @Schema(description = "登入使用者帳號", example = "tester")
        String username,

        @Schema(description = "遮罩後密碼", example = "******")
        String maskedPassword,

        @Schema(description = "遮罩後 SSH 私鑰標記", example = "[SSH_PRIVATE_KEY_CONFIGURED]")
        String maskedPrivateKey,

        @Schema(description = "遮罩後私鑰保護密碼", example = "******")
        String maskedPassphrase,

        @Schema(description = "是否已配置密碼", example = "true")
        boolean hasPassword,

        @Schema(description = "是否已配置私鑰", example = "false")
        boolean hasPrivateKey,

        @Schema(description = "是否已配置私鑰保護密碼", example = "false")
        boolean hasPassphrase,

        @Schema(description = "是否啟用", example = "true")
        boolean active,

        @Schema(description = "備註說明", example = "本機 Docker SFTP 測試環境連線憑證")
        String description,

        @Schema(description = "建立時間戳記")
        Instant createdAt,

        @Schema(description = "最後更新時間戳記")
        Instant updatedAt
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
