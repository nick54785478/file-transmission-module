package com.example.demo.application.shared.vo;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * 補償任務資料載體數值物件 (CompensationTask - Value Object Record)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>應用層共用數值物件 (Shared VO Record)，純粹 Java 領域物件，零外部框架依賴。</li>
 *   <li>封裝 Saga 補償交易所需之完整連線參數與欲清理目標，提供 Outbound Port 與 Application Service 之間傳遞。</li>
 * </ul>
 * </p>
 *
 * @param id 補償任務唯一識別碼 (可為 null 表示尚未持久化)
 * @param trackingId 關聯之傳輸追蹤作業識別碼，不可為空
 * @param actionType 補償行為類型，不可為空
 * @param serverConfig 遠端伺服器連線配置，不可為空
 * @param remotePath 欲處理之遠端檔案路徑，不可為空
 * @param retryCount 當前已重試次數
 * @param maxRetries 最大重試次數上限
 * @param status 補償任務當前狀態，不可為空
 * @param errorMessage 最近一次失敗錯誤原因
 * @param nextRetryAt 下一次預計重試時間戳記，不可為空
 * @param createdAt 建立時間戳記
 * @param updatedAt 更新時間戳記
 */
public record CompensationTask(
        Long id,
        String trackingId,
        CompensationAction actionType,
        TransmissionServerConfig serverConfig,
        String remotePath,
        int retryCount,
        int maxRetries,
        CompensationStatus status,
        String errorMessage,
        Instant nextRetryAt,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 精簡建構子：實施防禦性驗證
     */
    public CompensationTask {
        Objects.requireNonNull(trackingId, "trackingId 不能為空");
        Objects.requireNonNull(actionType, "actionType 不能為空");
        Objects.requireNonNull(serverConfig, "serverConfig 不能為空");
        Objects.requireNonNull(remotePath, "remotePath 不能為空");
        Objects.requireNonNull(status, "status 不能為空");
        Objects.requireNonNull(nextRetryAt, "nextRetryAt 不能為空");
        maxRetries = (maxRetries > 0) ? maxRetries : 5;
        retryCount = Math.max(0, retryCount);
    }

    /**
     * 快速建立初始 PENDING 補償任務
     *
     * @param trackingId 傳輸追蹤識別碼
     * @param actionType 補償動作
     * @param serverConfig 伺服器配置
     * @param remotePath 遠端目標路徑
     * @param errorMessage 初始失敗原因
     * @return 初始待重試補償任務
     */
    public static CompensationTask createPending(
            String trackingId,
            CompensationAction actionType,
            TransmissionServerConfig serverConfig,
            String remotePath,
            String errorMessage
    ) {
        Instant now = Instant.now();
        return new CompensationTask(
                null,
                trackingId,
                actionType,
                serverConfig,
                remotePath,
                0,
                5,
                CompensationStatus.PENDING,
                errorMessage,
                now,
                now,
                now
        );
    }
}
