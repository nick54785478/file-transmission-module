package com.example.demo.presentation.resource.in;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

/**
 * 檔案下載請求資料載體 (DownloadFileResource - Request Resource)
 * <p>
 * 命名規範: V + N + Resource (AGENTS.md 3-1)
 * <br>
 * <b>重要架構守則：</b>
 * 1. 嚴格作為純資料載體 (Pure Data Carrier)，以 Java record 實作，禁止包含任何領域業務或轉換邏輯 (如 toCommand)；
 * 2. 伺服器主機、通訊埠、協定與帳密金鑰已全面移入後端集中管理 (ServerCredential)，呼叫端僅需傳入憑證識別碼 (credentialId)；
 * 3. 表現層至應用層 Command 的防腐層轉換由 {@link com.example.demo.presentation.assembler.TransmissionResourceAssembler} 統一負責。
 * </p>
 */
@Schema(description = "檔案下載請求參數載體 (純化版：連線資訊由後端憑證庫管理)")
public record DownloadFileResource(
        @Schema(description = "後端伺服器憑證 ID 或別名 (例如: mock-sftp, mock-ftp, mock-ftps)", example = "mock-sftp", requiredMode = Schema.RequiredMode.REQUIRED)
        String credentialId,

        @Schema(description = "遠端目標檔案完整路徑", example = "/upload/sftp-uploaded-demo.txt", requiredMode = Schema.RequiredMode.REQUIRED)
        String remoteFilePath,

        @Schema(description = "本機儲存目錄或目標檔案完整路徑", example = "D:/桌面/ModuleWorkSpace/file-transmission-module/test-data/download/downloaded.txt", requiredMode = Schema.RequiredMode.REQUIRED)
        String savedLocalPath
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
