package com.example.demo.application.shared.query;

import java.io.Serializable;
import java.util.Objects;

/**
 * 查詢傳輸狀態條件物件 (GetTransmissionStatusQuery - Query)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>V + N + Query</code> (AGENTS.md 3-5)。</li>
 *   <li>位於 <code>application/shared/query/</code>。</li>
 *   <li>封裝傳輸追蹤唯一識別碼 (trackingId)，供查詢對應之傳輸階段即時生命週期與資料庫紀錄。</li>
 * </ul>
 * </p>
 */
public record GetTransmissionStatusQuery(String trackingId) implements Serializable {

    public GetTransmissionStatusQuery {
        Objects.requireNonNull(trackingId, "trackingId 不能為空");
    }

    /**
     * 靜態工廠方法
     *
     * @param trackingId 傳輸追蹤唯一識別碼
     * @return GetTransmissionStatusQuery 物件
     */
    public static GetTransmissionStatusQuery of(String trackingId) {
        return new GetTransmissionStatusQuery(trackingId);
    }
}
