package com.example.demo.config.config;

import com.example.demo.application.port.in.ProcessCompensationUseCase;
import com.example.demo.application.port.in.ReconcileTransmissionUseCase;
import com.example.demo.application.shared.dto.TransmissionReconciliationGottenResult;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 傳輸對帳與補償排程配置單元測試 (ReconciliationSchedulerConfigTest)
 * <p>
 * 遵循測試規範：
 * 1. 驗證定時排程方法觸發與開關控制；
 * 2. 測試方法均標註 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationSchedulerConfigTest {

    @Mock
    private ReconcileTransmissionUseCase reconcileTransmissionUseCase;

    @Mock
    private ProcessCompensationUseCase processCompensationUseCase;

    private ReconciliationSchedulerConfig schedulerConfig;

    @BeforeEach
    void setUp() {
        schedulerConfig = new ReconciliationSchedulerConfig(
                reconcileTransmissionUseCase,
                processCompensationUseCase
        );
        ReflectionTestUtils.setField(schedulerConfig, "reconciliationEnabled", true);
        ReflectionTestUtils.setField(schedulerConfig, "zombieThresholdMinutes", 30);
        ReflectionTestUtils.setField(schedulerConfig, "compensationEnabled", true);
    }

    /**
     * Feature: 背景對帳排程調度
     * Scenario: 啟用對帳排程時正確觸發 ReconcileTransmissionUseCase
     * Given: reconciliationEnabled 設為 true
     * When: 觸發 scheduleReconciliationSweep 方法
     * Then: 調用 reconcileTransmissionUseCase.reconcile
     */
    @Test
    @DisplayName("情境：排程觸發對帳掃描")
    void testScheduleReconciliationSweepWhenEnabled() {
        when(reconcileTransmissionUseCase.reconcile(any(Duration.class)))
                .thenReturn(TransmissionReconciliationGottenResult.empty());

        schedulerConfig.scheduleReconciliationSweep();

        verify(reconcileTransmissionUseCase).reconcile(Duration.ofMinutes(30));
    }

    /**
     * Feature: 背景對帳排程開關控制
     * Scenario: 停用對帳排程時不觸發 UseCase
     * Given: reconciliationEnabled 設為 false
     * When: 觸發 scheduleReconciliationSweep 方法
     * Then: 不調用 reconcileTransmissionUseCase
     */
    @Test
    @DisplayName("情境：停用對帳排程時略過執行")
    void testScheduleReconciliationSweepWhenDisabled() {
        ReflectionTestUtils.setField(schedulerConfig, "reconciliationEnabled", false);

        schedulerConfig.scheduleReconciliationSweep();

        verify(reconcileTransmissionUseCase, never()).reconcile(any());
    }

    /**
     * Feature: Saga 補償重試排程調度
     * Scenario: 啟用補償排程時正確觸發 ProcessCompensationUseCase
     * Given: compensationEnabled 設為 true
     * When: 觸發 scheduleCompensationRetry 方法
     * Then: 調用 processCompensationUseCase.processPendingCompensations
     */
    @Test
    @DisplayName("情境：排程觸發 Saga 補償 Outbox 掃描")
    void testScheduleCompensationRetryWhenEnabled() {
        when(processCompensationUseCase.processPendingCompensations()).thenReturn(2);

        schedulerConfig.scheduleCompensationRetry();

        verify(processCompensationUseCase).processPendingCompensations();
    }

    /**
     * Feature: Saga 補償重試排程開關控制
     * Scenario: 停用補償排程時不觸發 UseCase
     * Given: compensationEnabled 設為 false
     * When: 觸發 scheduleCompensationRetry 方法
     * Then: 不調用 processCompensationUseCase
     */
    @Test
    @DisplayName("情境：停用補償排程時略過執行")
    void testScheduleCompensationRetryWhenDisabled() {
        ReflectionTestUtils.setField(schedulerConfig, "compensationEnabled", false);

        schedulerConfig.scheduleCompensationRetry();

        verify(processCompensationUseCase, never()).processPendingCompensations();
    }
}
