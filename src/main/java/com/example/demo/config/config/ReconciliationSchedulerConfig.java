package com.example.demo.config.config;

import com.example.demo.application.port.in.ProcessCompensationUseCase;
import com.example.demo.application.port.in.ReconcileTransmissionUseCase;
import java.time.Duration;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 傳輸對帳與補償排程配置類 (ReconciliationSchedulerConfig - Configuration Layer)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>位於配置層 (<code>com.example.demo.config.config</code>，AGENTS.md 第 2 節 Configuration Layer)。</li>
 *   <li>啟用 Spring 排程機制 (<code>@EnableScheduling</code>)。</li>
 *   <li>定時調度 {@link ReconcileTransmissionUseCase} 執行超時殭屍任務對帳自癒。</li>
 *   <li>定時調度 {@link ProcessCompensationUseCase} 執行 Saga 補償交易 Outbox 表重試。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Configuration
@EnableScheduling
public class ReconciliationSchedulerConfig {

    private final ReconcileTransmissionUseCase reconcileTransmissionUseCase;
    private final ProcessCompensationUseCase processCompensationUseCase;

    @Value("${transmission.reconciliation.enabled:true}")
    private boolean reconciliationEnabled;

    @Value("${transmission.reconciliation.zombie-threshold-minutes:30}")
    private int zombieThresholdMinutes;

    @Value("${transmission.compensation.enabled:true}")
    private boolean compensationEnabled;

    /**
     * 建構排程配置類並注入 UseCase 依賴
     *
     * @param reconcileTransmissionUseCase 對帳自癒使用案例
     * @param processCompensationUseCase 補償重試使用案例
     */
    public ReconciliationSchedulerConfig(
            ReconcileTransmissionUseCase reconcileTransmissionUseCase,
            ProcessCompensationUseCase processCompensationUseCase
    ) {
        this.reconcileTransmissionUseCase = Objects.requireNonNull(reconcileTransmissionUseCase, "reconcileTransmissionUseCase 不能為空");
        this.processCompensationUseCase = Objects.requireNonNull(processCompensationUseCase, "processCompensationUseCase 不能為空");
    }

    /**
     * 定時執行背景對帳自癒排程 (預設每 10 分鐘，啟動後延遲 30 秒首次觸發)
     */
    @Scheduled(
            fixedDelayString = "${transmission.reconciliation.interval-ms:600000}",
            initialDelayString = "${transmission.reconciliation.initial-delay-ms:30000}"
    )
    public void scheduleReconciliationSweep() {
        if (!reconciliationEnabled) {
            return;
        }
        try {
            log.debug("[排程任務] 發起傳輸對帳自癒掃描 (門檻: {} 分鐘)", zombieThresholdMinutes);
            reconcileTransmissionUseCase.reconcile(Duration.ofMinutes(zombieThresholdMinutes));
        } catch (Exception ex) {
            log.error("[排程任務] 執行對帳自癒排程異常: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 定時執行 Saga 補償交易重試排程 (預設每 1 分鐘，啟動後延遲 15 秒首次觸發)
     */
    @Scheduled(
            fixedDelayString = "${transmission.compensation.interval-ms:60000}",
            initialDelayString = "${transmission.compensation.initial-delay-ms:15000}"
    )
    public void scheduleCompensationRetry() {
        if (!compensationEnabled) {
            return;
        }
        try {
            log.debug("[排程任務] 發起 Saga 補償交易 Outbox 掃描");
            processCompensationUseCase.processPendingCompensations();
        } catch (Exception ex) {
            log.error("[排程任務] 執行補償重試排程異常: {}", ex.getMessage(), ex);
        }
    }
}
