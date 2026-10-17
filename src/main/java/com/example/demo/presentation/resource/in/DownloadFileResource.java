package com.example.demo.presentation.resource.in;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

/**
 * 檔案下載請求資料載體 (DownloadFileResource - Request Resource)
 * <p>
 * 命名規範: V + N + Resource (AGENTS.md 3-1)
 * <br>
 * <b>重要架構守則：</b>
 * 嚴格作為純資料載體 (Pure Data Carrier)，以 Java record 實作，禁止包含任何領域業務或轉換邏輯 (如 toCommand)；
 * 資料轉換由 {@link com.example.demo.presentation.assembler.TransmissionResourceAssembler} 統一負責。
 * 覆寫 toString 實施密碼與私鑰遮罩脫敏，防止日誌意外洩漏機密。
 * </p>
 */
@Schema(description = "檔案下載請求參數載體")
public record DownloadFileResource(
        @Schema(description = "遠端伺服器主機名稱或 IP", example = "sftp.example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String host,

        @Schema(description = "遠端伺服器通訊埠 (SFTP 預設 22, FTP 預設 21)", example = "22")
        Integer port,

        @Schema(description = "傳輸協定 (SFTP / FTP / FTPS)", example = "SFTP", requiredMode = Schema.RequiredMode.REQUIRED)
        String protocol,

        @Schema(description = "登入使用者帳號", example = "sftp_user", requiredMode = Schema.RequiredMode.REQUIRED)
        String username,

        @Schema(description = "登入密碼 (支援明文或 ENC(...) 加密密文)", example = "Password123")
        String password,

        @Schema(description = "遠端目標檔案完整路徑", example = "/remote/data/file.txt", requiredMode = Schema.RequiredMode.REQUIRED)
        String remoteFilePath,

        @Schema(description = "本機儲存目錄或目標檔案完整路徑", example = "D:/downloads/file.txt", requiredMode = Schema.RequiredMode.REQUIRED)
        String savedLocalPath,

        @Schema(description = "逾時毫秒數 (0 為預設逾時)", example = "10000")
        Integer timeoutMs,

        @Schema(description = "是否啟用被動模式 (Passive Mode，預設為 true)", example = "true")
        Boolean passiveMode,

        @Schema(description = "是否嚴格檢查主機金鑰 (Strict Host Key Checking，預設為 false)", example = "false")
        Boolean strictHostKeyChecking,

        @Schema(description = "SSH 私鑰內容 (PEM 格式) 或本機私鑰檔案路徑 (用於 SFTP 金鑰認證，選填)", example = "D:/keys/id_rsa")
        String privateKey,

        @Schema(description = "SSH 私鑰解密密碼 (支援明文或 ENC(...) 加密密文，選填)", example = "passphrase123")
        String passphrase
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Compact Constructor：處理選填欄位預設值，支援 Jackson 反序列化缺省鍵值
     */
    public DownloadFileResource {
        if (port == null) {
            port = 0;
        }
        if (timeoutMs == null) {
            timeoutMs = 0;
        }
        if (passiveMode == null) {
            passiveMode = Boolean.TRUE;
        }
        if (strictHostKeyChecking == null) {
            strictHostKeyChecking = Boolean.FALSE;
        }
    }

    @Override
    public String toString() {
        String maskedPassword = (password == null || password.isEmpty()) ? "" : "******";
        String maskedPassphrase = (passphrase == null || passphrase.isEmpty()) ? "" : "******";
        String maskedKey = (privateKey == null || privateKey.isEmpty()) ? "" : "[CONFIGURED]";
        return "DownloadFileResource[" +
                "host=" + host +
                ", port=" + port +
                ", protocol=" + protocol +
                ", username=" + username +
                ", password=" + maskedPassword +
                ", remoteFilePath=" + remoteFilePath +
                ", savedLocalPath=" + savedLocalPath +
                ", timeoutMs=" + timeoutMs +
                ", passiveMode=" + passiveMode +
                ", strictHostKeyChecking=" + strictHostKeyChecking +
                ", privateKey=" + maskedKey +
                ", passphrase=" + maskedPassphrase +
                ']';
    }
}
