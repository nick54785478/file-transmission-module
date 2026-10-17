package com.example.demo.presentation.resource.out;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.Instant;

/**
 * 非同步傳輸任務已提交回應資源載體 (FileTransmissionSubmittedResource - Response Resource)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>N + Ved + Resource</code> (AGENTS.md 3-2)。</li>
 *   <li>位於 <code>presentation/resource/out/</code>。</li>
 *   <li>嚴格作為純資料載體 (Pure Data Carrier)，禁止包含任何領域或業務邏輯。</li>
 *   <li>對外回傳非同步提交成功的 trackingId、目前進行中狀態與提交時間戳記。</li>
 * </ul>
 * </p>
 */
@Schema(description = "非同步傳輸長任務已提交回應資料載體 (202 Accepted)")
public record FileTransmissionSubmittedResource(
        @Schema(description = "傳輸作業追蹤唯一識別碼 (Tracking ID)", example = "tx-12345678")
        String trackingId,

        @Schema(description = "使用的傳輸協定", example = "SFTP")
        String protocol,

        @Schema(description = "目前作業狀態 (固定為 IN_PROGRESS)", example = "IN_PROGRESS")
        String status,

        @Schema(description = "狀態提示訊息", example = "檔案上傳任務已提交至背景執行緒執行")
        String message,

        @Schema(description = "任務提交時間戳記", example = "2026-10-17T03:00:00Z")
        Instant submittedAt
) implements Serializable {
}
