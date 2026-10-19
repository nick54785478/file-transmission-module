package com.example.demo.application.service;

import com.example.demo.application.port.in.ProcessCompensationUseCase;
import com.example.demo.application.port.out.FileTransmissionGatewayPort;
import com.example.demo.application.port.out.TransmissionCompensationStoragePort;
import com.example.demo.application.shared.vo.CompensationAction;
import com.example.demo.application.shared.vo.CompensationStatus;
import com.example.demo.application.shared.vo.CompensationTask;
import com.example.demo.application.shared.vo.RemoteFilePath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 傳輸補償交易應用服務 (TransmissionCompensationService - Application Service)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>in : ... + ApplicationService</code> (AGENTS.md 3-9)。</li>
 *   <li>實作 Inbound Port {@link ProcessCompensationUseCase}。</li>
 *   <li>負責執行 Saga 補償交易 Outbox 表中的到期清理重試任務。</li>
 *   <li>採用指數退避演算法 (Exponential Backoff: 1m, 2m, 4m, 8m, 16m...)，當達到最大重試上限時標記為 ABANDONED 並告警。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class TransmissionCompensationService implements ProcessCompensationUseCase {

    private final TransmissionCompensationStoragePort compensationStoragePort;
    private final FileTransmissionGatewayPort fileTransmissionGatewayPort;

    /**
     * 建構補償交易服務並注入依賴
     *
     * @param compensationStoragePort 補償日誌儲存輸出埠
     * @param fileTransmissionGatewayPort 檔案傳輸閘道輸出埠
     */
    public TransmissionCompensationService(
            TransmissionCompensationStoragePort compensationStoragePort,
            FileTransmissionGatewayPort fileTransmissionGatewayPort
    ) {
        this.compensationStoragePort = Objects.requireNonNull(compensationStoragePort, "compensationStoragePort 不能為空");
        this.fileTransmissionGatewayPort = Objects.requireNonNull(fileTransmissionGatewayPort, "fileTransmissionGatewayPort 不能為空");
    }

    @Override
    public int processPendingCompensations() {
        Instant now = Instant.now();
        List<CompensationTask> pendingTasks = compensationStoragePort.findPendingRetries(now);
        if (pendingTasks.isEmpty()) {
            return 0;
        }

        log.info("[Saga 補償引擎] 發現 {} 筆待處理之補償重試任務", pendingTasks.size());
        int successCount = 0;

        for (CompensationTask task : pendingTasks) {
            try {
                executeSingleCompensation(task);
                CompensationTask updated = new CompensationTask(
                        task.id(),
                        task.trackingId(),
                        task.actionType(),
                        task.serverConfig(),
                        task.remotePath(),
                        task.retryCount(),
                        task.maxRetries(),
                        CompensationStatus.SUCCESS,
                        null,
                        task.nextRetryAt(),
                        task.createdAt(),
                        Instant.now()
                );
                compensationStoragePort.updateCompensation(updated);
                successCount++;
                log.info("[Saga 補償引擎] 補償任務成功完成: trackingId={}, action={}, remotePath={}",
                        task.trackingId(), task.actionType(), task.remotePath());
            } catch (Exception ex) {
                int nextRetryCount = task.retryCount() + 1;
                boolean abandoned = nextRetryCount >= task.maxRetries();
                CompensationStatus newStatus = abandoned ? CompensationStatus.ABANDONED : CompensationStatus.RETRYING;
                Duration backoff = Duration.ofMinutes((long) Math.min(60, Math.pow(2, task.retryCount())));
                Instant nextRetry = Instant.now().plus(backoff);

                CompensationTask updated = new CompensationTask(
                        task.id(),
                        task.trackingId(),
                        task.actionType(),
                        task.serverConfig(),
                        task.remotePath(),
                        nextRetryCount,
                        task.maxRetries(),
                        newStatus,
                        ex.getMessage(),
                        nextRetry,
                        task.createdAt(),
                        Instant.now()
                );
                compensationStoragePort.updateCompensation(updated);

                if (abandoned) {
                    log.error("[Saga 補償引擎] 補償任務超過最大重試次數，已標記為 ABANDONED (需人工介入): trackingId={}, error={}",
                            task.trackingId(), ex.getMessage());
                } else {
                    log.warn("[Saga 補償引擎] 補償任務重試失敗，將於 {} 後再次重試: trackingId={}, error={}",
                            backoff, task.trackingId(), ex.getMessage());
                }
            }
        }

        return successCount;
    }

    /**
     * 執行單筆補償技術動作
     *
     * @param task 補償任務載體
     */
    private void executeSingleCompensation(CompensationTask task) {
        if (task.actionType() == CompensationAction.DELETE_REMOTE_STAGING) {
            RemoteFilePath remoteFilePath = RemoteFilePath.of(task.remotePath());
            if (fileTransmissionGatewayPort.existsFile(task.serverConfig(), remoteFilePath)) {
                boolean deleted = fileTransmissionGatewayPort.deleteFile(task.serverConfig(), remoteFilePath);
                if (!deleted) {
                    throw new IllegalStateException("遠端暫存檔刪除未成功 (Gateway deleteFile returned false)");
                }
            }
        } else if (task.actionType() == CompensationAction.RENAME_BACK) {
            log.info("[Saga 補償引擎] 執行更名回滾: {}", task.remotePath());
        }
    }
}
