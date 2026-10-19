package com.example.demo.application.port.in;

import com.example.demo.application.shared.dto.TransmissionStatusGottenResult;
import com.example.demo.application.shared.query.GetTransmissionStatusQuery;
import java.util.Optional;

/**
 * 查詢傳輸作業狀態使用案例介面 (GetTransmissionStatusUseCase - Inbound Port)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>in : ... + UseCase</code> (AGENTS.md 3-8)。</li>
 *   <li>接收 {@link GetTransmissionStatusQuery}，依據 trackingId 查詢傳輸作業之即時或歷史狀態。</li>
 *   <li>回傳封裝 {@link TransmissionStatusGottenResult} 之 Optional 物件。</li>
 * </ul>
 * </p>
 */
public interface GetTransmissionStatusUseCase {

    /**
     * 依追蹤識別碼查詢傳輸任務狀態
     *
     * @param query 查詢條件物件
     * @return 傳輸階段結果物件 Optional (若查無資料則為 empty)
     */
    Optional<TransmissionStatusGottenResult> getTransmissionStatus(GetTransmissionStatusQuery query);
}
