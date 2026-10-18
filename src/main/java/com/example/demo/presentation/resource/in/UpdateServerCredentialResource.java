package com.example.demo.presentation.resource.in;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

/**
 * 更新伺服器傳輸憑證請求資料載體 (UpdateServerCredentialResource - Request Resource)
 * <p>
 * <b>架構與命名規範 (AGENTS.md 2 與 3-1)：</b>
 * 命名規範: <code>V + N + Resource</code>。
 * 位於表現層 <code>presentation/resource/in/</code>，作為更新憑證的純資料載體。
 * </p>
 */
@Schema(description = "更新伺服器傳輸憑證請求資料")
public record UpdateServerCredentialResource(
        @Schema(description = "憑證易讀別名", example = "mock-sftp")
        String alias,

        @Schema(description = "伺服器主機名稱或 IP", example = "127.0.0.1")
        String host,

        @Schema(description = "伺服器通訊埠", example = "2222")
        Integer port,

        @Schema(description = "傳輸協定 (SFTP / FTP / FTPS)", example = "SFTP")
        String protocol,

        @Schema(description = "登入使用者帳號", example = "tester")
        String username,

        @Schema(description = "登入密碼 (若不更新請留空)", nullable = true)
        String password,

        @Schema(description = "SSH 私鑰內容 (若不更新請留空)", nullable = true)
        String privateKey,

        @Schema(description = "SSH 私鑰解密密碼 (若不更新請留空)", nullable = true)
        String passphrase,

        @Schema(description = "憑證備註與用途描述", example = "已更新備註")
        String description,

        @Schema(description = "是否處於啟用狀態", example = "true")
        Boolean active
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
