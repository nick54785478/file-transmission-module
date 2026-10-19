package com.example.demo.infrastructure.persistence.transmission.repository;

import com.example.demo.infrastructure.persistence.transmission.entity.TransmissionSessionItemEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 目錄同步單檔項目檢查點 JPA 資料庫存取倉庫 (TransmissionSessionItemJpaRepository - Spring Data JPA Repository)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>... + JpaRepository</code> (AGENTS.md 第 2 節)。</li>
 *   <li>位於 <code>infrastructure/persistence/transmission/repository/</code>。</li>
 *   <li>負責 <code>transmission_session_items</code> 之儲存與查詢。</li>
 * </ul>
 * </p>
 */
@Repository
public interface TransmissionSessionItemJpaRepository extends JpaRepository<TransmissionSessionItemEntity, Long> {

    /**
     * 依工作階段識別碼查詢所有子項目傳輸記錄
     *
     * @param sessionId 傳輸工作階段唯一識別碼
     * @return 該工作階段的所有子項目實體清單
     */
    List<TransmissionSessionItemEntity> findBySessionId(String sessionId);
}
