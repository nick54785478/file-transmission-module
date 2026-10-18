package com.example.demo.presentation.resource.in;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * 目錄同步請求資料載體 (SyncDirectoryResource - Request Resource)
 * <p>
 * 命名規範: V + N + Resource (AGENTS.md 3-1)
 * <br>
 * <b>重要架構守則：</b>
 * 1. 嚴格作為純資料載體 (Pure Data Carrier)，以 Java record 實作，禁止包含任何領域業務或轉換邏輯 (如 toCommand)；
 * 2. 伺服器主機、通訊埠、協定與帳密金鑰已全面移入後端集中管理 (ServerCredential)，呼叫端僅需傳入憑證識別碼 (credentialId)；
 * 3. 表現層至應用層 Command 的防腐層轉換由 {@link com.example.demo.presentation.assembler.TransmissionResourceAssembler} 統一負責；
 * 4. 透過 Jakarta Bean Validation 註解實作基本參數檢核防禦。
 * </p>
 */
@Schema(description = "目錄同步請求參數載體 (純化版：連線資訊由後端憑證庫管理)")
public record SyncDirectoryResource(
        @NotBlank(message = "後端伺服器憑證 ID (credentialId) 不能為空")
        @Schema(description = "後端伺服器憑證 ID 或別名 (例如: mock-sftp, mock-ftp, mock-ftps)", example = "mock-sftp", requiredMode = Schema.RequiredMode.REQUIRED)
        String credentialId,

        @NotBlank(message = "本機同步目錄完整路徑 (localDir) 不能為空")
        @Schema(description = "本機同步目錄完整路徑", example = "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/local/sync-source", requiredMode = Schema.RequiredMode.REQUIRED)
        String localDir,

        @NotBlank(message = "遠端同步目標目錄 (remoteDir) 不能為空")
        @Schema(description = "遠端同步目標目錄", example = "/upload/sync-target", requiredMode = Schema.RequiredMode.REQUIRED)
        String remoteDir,

        @Pattern(regexp = "^(LOCAL_TO_REMOTE|REMOTE_TO_LOCAL)$", message = "同步方向 (direction) 僅允許 LOCAL_TO_REMOTE 或 REMOTE_TO_LOCAL")
        @Schema(description = "同步方向 (LOCAL_TO_REMOTE 或 REMOTE_TO_LOCAL，預設為 LOCAL_TO_REMOTE)", example = "LOCAL_TO_REMOTE", nullable = true)
        String direction,

        @Schema(description = "是否同步刪除目標端多餘檔案 (預設為 false)", example = "false", nullable = true)
        Boolean deleteRemoteExtras
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Compact Constructor：處理選填欄位預設值，支援 Jackson 反序列化缺省鍵值
     */
    public SyncDirectoryResource {
        if (direction == null || direction.isBlank()) {
            direction = "LOCAL_TO_REMOTE";
        }
        if (deleteRemoteExtras == null) {
            deleteRemoteExtras = Boolean.FALSE;
        }
    }
}
