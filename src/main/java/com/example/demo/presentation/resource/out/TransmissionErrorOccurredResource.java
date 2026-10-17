package com.example.demo.presentation.resource.out;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

/**
 * 傳輸錯誤發生回應資料載體 (TransmissionErrorOccurredResource - Response Resource)
 * <p>
 * 命名規範: N + Ved + Resource (AGENTS.md 3-2)
 * <br>
 * <b>架構與設計職責：</b>
 * <ul>
 *   <li>屬於表現層輸出資料載體 (Response Data Carrier)，以 Java record 實作，用於 API 發生例外異常時回傳標準化錯誤訊息。</li>
 *   <li><b>重要架構守則：</b>嚴格作為純資料載體 (Pure Data Carrier)，禁止包含任何業務或轉換邏輯；
 *       物件建立由 {@link com.example.demo.presentation.assembler.TransmissionResourceAssembler} 統一負責。</li>
 * </ul>
 * </p>
 */
@Schema(description = "傳輸作業錯誤回應資料載體")
public record TransmissionErrorOccurredResource(
        @Schema(description = "錯誤發生時間戳記 (ISO-8601 格式)", example = "2026-10-17T03:00:00Z")
        String timestamp,

        @Schema(description = "HTTP 狀態碼", example = "400")
        int status,

        @Schema(description = "HTTP 狀態原因說明", example = "Bad Request")
        String error,

        @Schema(description = "詳細錯誤訊息說明", example = "不支援的傳輸協定: UNKNOWN")
        String message,

        @Schema(description = "觸發異常之請求 URI 路徑", example = "/api/transmission/upload")
        String path,

        @Schema(description = "觸發之例外類別簡稱", example = "IllegalArgumentException")
        String exception
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
