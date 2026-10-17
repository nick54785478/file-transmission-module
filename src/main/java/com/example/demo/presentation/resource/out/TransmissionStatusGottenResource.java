package com.example.demo.presentation.resource.out;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

/**
 * 傳輸狀態查詢回應資源載體 (TransmissionStatusGottenResource - Response Resource)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>N + Gotten/Searched + Resource</code> (AGENTS.md 3-2)。</li>
 *   <li>位於 <code>presentation/resource/out/</code>。</li>
 *   <li>嚴格作為純資料載體 (Pure Data Carrier)。</li>
 *   <li>呈現傳輸作業完整執行狀態、來源與目標路徑、檔案大小、耗時及錯誤原因。</li>
 * </ul>
 * </p>
 */
@Schema(description = "傳輸作業狀態與結果查詢回應載體")
public record TransmissionStatusGottenResource(
        @Schema(description = "傳輸作業追蹤唯一識別碼 (Tracking ID)", example = "tx-12345678")
        String transmissionId,

        @Schema(description = "使用的傳輸協定", example = "SFTP")
        String protocol,

        @Schema(description = "目前作業狀態 (IN_PROGRESS, COMPLETED, FAILED)", example = "COMPLETED")
        String status,

        @Schema(description = "來源路徑", example = "D:/data/upload.txt")
        String sourcePath,

        @Schema(description = "目標路徑", example = "/remote/upload")
        String targetPath,

        @Schema(description = "檔案名稱", example = "upload.txt")
        String fileName,

        @Schema(description = "檔案大小 (Bytes)", example = "2048")
        Long fileSizeBytes,

        @Schema(description = "傳輸總耗時 (毫秒)", example = "150")
        Long durationMillis,

        @Schema(description = "錯誤訊息 (若成功則為 null)", example = "null")
        String errorMessage
) implements Serializable {
}
