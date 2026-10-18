package com.example.demo.presentation.resource.in;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 建立伺服器傳輸憑證請求資料載體 (CreateServerCredentialResource - Request Resource)
 * <p>
 * <b>架構與命名規範 (AGENTS.md 2 與 3-1)：</b>
 * <ul>
 *   <li>命名規範: <code>V + N + Resource</code>。</li>
 *   <li>位於表現層 <code>presentation/resource/in/</code>。</li>
 *   <li><b>純資料載體 (Pure Data Carrier)：</b> 嚴格禁止於內部包含轉換邏輯，轉換由獨立之 Assembler 負責。</li>
 * </ul>
 * </p>
 */
@Schema(description = "建立伺服器傳輸憑證請求資料")
public record CreateServerCredentialResource(
        @NotBlank(message = "憑證別名 (alias) 不能為空")
        @Schema(description = "憑證易讀別名 (唯一識別，如 mock-sftp)", example = "mock-sftp", requiredMode = Schema.RequiredMode.REQUIRED)
        String alias,

        @Schema(description = "伺服器主機名稱或 IP", example = "127.0.0.1")
        String host,

        @Schema(description = "伺服器通訊埠 (SFTP 預設 22, Docker SFTP 2222, FTP 2121)", example = "2222")
        Integer port,

        @Schema(description = "傳輸協定 (SFTP / FTP / FTPS)", example = "SFTP")
        String protocol,

        @NotBlank(message = "登入使用者帳號 (username) 不能為空")
        @Schema(description = "登入使用者帳號", example = "tester", requiredMode = Schema.RequiredMode.REQUIRED)
        String username,

        @Schema(description = "登入密碼 (支援明文或 ENC(...) 加密密文；金鑰認證時可留空)", example = "password")
        String password,

        @Schema(description = "SSH 私鑰內容 (PEM 格式) 或私鑰檔案路徑 (用於 SFTP 金鑰認證；帳密認證請留空)", nullable = true)
        String privateKey,

        @Schema(description = "SSH 私鑰解密密碼 (支援明文或 ENC(...) 加密密文；帳密認證請留空)", nullable = true)
        String passphrase,

        @Schema(description = "憑證備註與用途描述", example = "本機 Docker SFTP 測試環境連線憑證")
        String description
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
