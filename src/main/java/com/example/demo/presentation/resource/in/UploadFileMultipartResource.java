package com.example.demo.presentation.resource.in;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * Multipart 檔案上傳請求資料載體 (UploadFileMultipartResource - Request Resource)
 * <p>
 * 命名規範: V + N + Resource (AGENTS.md 3-1)
 * <br>
 * <b>重要架構守則：</b>
 * 1. 嚴格作為純資料載體 (Pure Data Carrier)，以 Java record 實作，禁止包含任何領域業務或轉換邏輯；
 * 2. 伺服器主機、通訊埠、協定與帳密金鑰已全面移入後端集中管理 (ServerCredential)，呼叫端僅需傳入憑證識別碼 (credentialId)；
 * 3. 表現層至應用層 Command 的防腐層轉換由 {@link com.example.demo.presentation.assembler.TransmissionResourceAssembler} 統一負責；
 * 4. 透過 Jakarta Bean Validation 註解實作基本參數檢核防禦。
 * </p>
 */
@Schema(description = "Multipart 檔案上傳表單參數載體 (純化版：連線資訊由後端憑證庫管理)")
public record UploadFileMultipartResource(
        @NotBlank(message = "後端伺服器憑證 ID (credentialId) 不能為空")
        @Schema(description = "後端伺服器憑證 ID 或別名 (例如: mock-sftp, mock-ftp, mock-ftps)", example = "mock-sftp", requiredMode = Schema.RequiredMode.REQUIRED)
        String credentialId,

        @NotBlank(message = "遠端目標工作目錄 (remotePath) 不能為空")
        @Schema(description = "遠端目標工作目錄", example = "/upload", requiredMode = Schema.RequiredMode.REQUIRED)
        String remotePath,

        @Schema(description = "遠端儲存檔名 (若留空則沿用上傳檔案原始名稱)", example = "sftp-uploaded-demo.txt", nullable = true)
        String filename
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
