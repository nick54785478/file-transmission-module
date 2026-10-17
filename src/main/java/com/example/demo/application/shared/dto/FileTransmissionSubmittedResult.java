package com.example.demo.application.shared.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * 非同步傳輸任務提交結果資料載體 (FileTransmissionSubmittedResult - DTO)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>N + Gotten/Searched/Submitted + Result</code> (AGENTS.md 3-6)。</li>
 *   <li>位於 <code>application/shared/dto/</code>，嚴格作為純資料載體 (Pure Data Carrier)。</li>
 *   <li>當呼叫非同步傳輸使用案例時立即回傳本 DTO，攜帶生成的 trackingId 與目前進行中狀態 (IN_PROGRESS)。</li>
 * </ul>
 * </p>
 */
public record FileTransmissionSubmittedResult(
        String trackingId,
        String protocol,
        String status,
        String message,
        Instant submittedAt
) implements Serializable {
}
