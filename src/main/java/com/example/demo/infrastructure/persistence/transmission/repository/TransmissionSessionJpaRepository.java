package com.example.demo.infrastructure.persistence.transmission.repository;

import com.example.demo.infrastructure.persistence.transmission.entity.TransmissionSessionEntity;
import com.example.demo.infrastructure.persistence.transmission.vo.TransmissionStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 檔案傳輸作業階段 JPA 資料庫存取倉庫 (TransmissionSessionJpaRepository - Spring Data JPA Repository)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>... + JpaRepository</code> (AGENTS.md 第 2 節)。</li>
 *   <li>位於 <code>infrastructure/persistence/transmission/repository/</code>。</li>
 *   <li>繼承 Spring Data JPA 之 <code>JpaRepository</code>，負責 <code>TransmissionSessionJpaEntity</code> 之持久化操作。</li>
 *   <li>提供依狀態 (TransmissionStatus) 與通訊協定 (protocol) 查詢傳輸作業階段歷史之查詢合約。</li>
 * </ul>
 * </p>
 */
@Repository
public interface TransmissionSessionJpaRepository extends JpaRepository<TransmissionSessionEntity, String> {

    /**
     * 依傳輸作業狀態查詢所有傳輸階段記錄
     *
     * @param status 傳輸狀態 (PENDING, IN_PROGRESS, COMPLETED, FAILED)
     * @return 符合狀態之傳輸實體清單
     */
    List<TransmissionSessionEntity> findByStatus(TransmissionStatus status);

    /**
     * 依傳輸協定查詢所有傳輸階段記錄
     *
     * @param protocol 傳輸協定 (FTP, FTPS, SFTP)
     * @return 符合協定之傳輸實體清單
     */
    List<TransmissionSessionEntity> findByProtocol(String protocol);
}
