package com.example.demo.infrastructure.persistence.transmission.vo;

/**
 * 檔案傳輸實體狀態列舉 (TransmissionStatus - Entity Status)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>位於 <code>infrastructure/persistence/transmission/entity/</code>。</li>
 *   <li>代表傳輸工作階段實體 (TransmissionSession) 與 JPA ORM 實體生命週期中的各個狀態階段。</li>
 * </ul>
 * </p>
 */
public enum TransmissionStatus {

    /**
     * 傳輸準備中 (初始狀態)
     */
    PENDING,

    /**
     * 傳輸進行中 (已發起連線或正在傳輸檔案串流)
     */
    IN_PROGRESS,

    /**
     * 傳輸成功完成
     */
    COMPLETED,

    /**
     * 傳輸失敗 (通訊錯誤、逾時或認證失敗)
     */
    FAILED
}
