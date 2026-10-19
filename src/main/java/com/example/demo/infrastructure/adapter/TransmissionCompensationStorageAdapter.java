package com.example.demo.infrastructure.adapter;

import com.example.demo.application.port.out.TransmissionCompensationStoragePort;
import com.example.demo.application.shared.vo.CompensationTask;
import com.example.demo.infrastructure.persistence.transmission.entity.TransmissionCompensationEntity;
import com.example.demo.infrastructure.persistence.transmission.repository.TransmissionCompensationJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 傳輸補償任務儲存基礎設施適配器 (TransmissionCompensationStorageAdapter - Outbound Adapter)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>out : ... + Adapter</code> (AGENTS.md 3-9)。</li>
 *   <li>實作應用層 Outbound Port (TransmissionCompensationStoragePort)。</li>
 *   <li>呼叫 <code>TransmissionCompensationJpaRepository</code> 執行實體存取與轉換。</li>
 * </ul>
 * </p>
 */
@Component
public class TransmissionCompensationStorageAdapter implements TransmissionCompensationStoragePort {

    private final TransmissionCompensationJpaRepository repository;

    /**
     * 建構適配器並注入 JPA Repository
     *
     * @param repository 補償日誌 JPA 資料庫存取倉庫
     */
    public TransmissionCompensationStorageAdapter(TransmissionCompensationJpaRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能為空");
    }

    @Override
    public CompensationTask saveCompensation(CompensationTask task) {
        Objects.requireNonNull(task, "task 不能為空");
        TransmissionCompensationEntity entity = TransmissionCompensationEntity.fromDomain(task);
        TransmissionCompensationEntity saved = repository.save(entity);
        return saved.toDomain();
    }

    @Override
    public List<CompensationTask> findPendingRetries(Instant now) {
        Objects.requireNonNull(now, "now 不能為空");
        return repository.findPendingRetries(now)
                .stream()
                .map(TransmissionCompensationEntity::toDomain)
                .toList();
    }

    @Override
    public void updateCompensation(CompensationTask task) {
        Objects.requireNonNull(task, "task 不能為空");
        if (task.id() == null) {
            saveCompensation(task);
            return;
        }

        repository.findById(task.id()).ifPresent(entity -> {
            entity.setStatus(task.status());
            entity.setRetryCount(task.retryCount());
            entity.setErrorMessage(task.errorMessage());
            entity.setNextRetryAt(task.nextRetryAt());
            entity.setUpdatedAt(Instant.now());
            repository.save(entity);
        });
    }
}
