package com.example.demo.presentation.resource.out;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

/**
 * 檔案下載完成回應資料載體 (FileDownloadedResource - Response Resource)
 * <p>
 * 命名規範: N + Ved + Resource (AGENTS.md 3-2)
 * <br>
 * <b>重要架構守則：</b>
 * 嚴格作為純資料載體 (Pure Data Carrier)，以 Java record 實作，禁止包含任何業務或轉換邏輯；
 * 轉換由 {@link com.example.demo.presentation.assembler.TransmissionResourceAssembler} 統一負責。
 * </p>
 */
@Schema(description = "檔案下載完成回應資料載體")
public record FileDownloadedResource(
        @Schema(description = "傳輸作業追蹤唯一識別碼", example = "tx-12345678")
        String transmissionId,

        @Schema(description = "使用的傳輸協定", example = "SFTP")
        String protocol,

        @Schema(description = "傳輸狀態 (COMPLETED, FAILED 等)", example = "COMPLETED")
        String status,

        @Schema(description = "遠端來源檔案路徑", example = "/remote/data/file.txt")
        String sourcePath,

        @Schema(description = "本機儲存檔案完整路徑", example = "D:/downloads/file.txt")
        String targetPath,

        @Schema(description = "下載檔案名稱", example = "file.txt")
        String fileName,

        @Schema(description = "檔案大小 (Bytes)", example = "4096")
        long fileSizeBytes,

        @Schema(description = "傳輸作業耗時 (毫秒)", example = "210")
        long durationMillis,

        @Schema(description = "錯誤訊息 (若成功則為 null)", example = "null")
        String errorMessage
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
