package com.example.demo.infrastructure.persistence.transmission.repository;

import com.example.demo.infrastructure.persistence.transmission.entity.TransmissionCompensationEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Saga 補償交易日誌 JPA 資料庫存取倉庫 (TransmissionCompensationJpaRepository - Spring Data JPA Repository)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>... + JpaRepository</code> (AGENTS.md 第 2 節)。</li>
 *   <li>位於 <code>infrastructure/persistence/transmission/repository/</code>。</li>
 *   <li>負責 <code>transmission_compensation_log</code> 資料表之 CRUD 操作與到期重試任務查詢。</li>
 * </ul>
 * </p>
 */
@Repository
public interface TransmissionCompensationJpaRepository extends JpaRepository<TransmissionCompensationEntity, Long> {

    /**
     * 查詢所有狀態為 PENDING 或 RETRYING 且下次重試時間不大於目前時間之補償任務
     *
     * @param now 目前時間戳記 (UTC Instant)
     * @return 到期待執行的補償日誌實體清單 (依 nextRetryAt 由先至後排序)
     */
    @Query("SELECT c FROM TransmissionCompensationEntity c WHERE c.status IN (com.example.demo.application.shared.vo.CompensationStatus.PENDING, com.example.demo.application.shared.vo.CompensationStatus.RETRYING) AND c.nextRetryAt <= :now ORDER BY c.nextRetryAt ASC")
    List<TransmissionCompensationEntity> findPendingRetries(@Param("now") Instant now);

    /**
     * 依傳輸作業追蹤唯一識別碼查詢對應之補償日誌實體清單
     *
     * @param trackingId 傳輸追蹤作業識別碼
     * @return 補償實體清單
     */
    List<TransmissionCompensationEntity> findByTrackingId(String trackingId);
}
