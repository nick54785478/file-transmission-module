package com.example.demo.application.port.in;

import com.example.demo.application.shared.dto.TransmissionReconciliationGottenResult;
import java.time.Duration;

/**
 * 傳輸作業對帳自癒使用案例介面 (ReconcileTransmissionUseCase - Inbound Port)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>in : ... + UseCase</code> (AGENTS.md 3-8)。</li>
 *   <li>六角形架構 Inbound Port，負責掃描超時停滯於 IN_PROGRESS 狀態之殭屍任務，主動向遠端伺服器探測並執行狀態自癒晉升或殘留清理。</li>
 * </ul>
 * </p>
 */
public interface ReconcileTransmissionUseCase {

    /**
     * 掃描並自癒停滯超過指定閾值之傳輸工作階段
     *
     * @param zombieThreshold 判定為殭屍任務之超時時長閾值 (例如 Duration.ofMinutes(30))
     * @return TransmissionReconciliationGottenResult 對帳自癒統計成果
     */
    TransmissionReconciliationGottenResult reconcile(Duration zombieThreshold);
}
