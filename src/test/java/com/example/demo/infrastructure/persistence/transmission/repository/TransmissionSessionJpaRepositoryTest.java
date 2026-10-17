package com.example.demo.infrastructure.persistence.transmission.repository;

import com.example.demo.infrastructure.persistence.transmission.entity.TransmissionSessionEntity;
import com.example.demo.infrastructure.persistence.transmission.vo.TransmissionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 檔案傳輸作業階段 JPA Repository 整合測試 (TransmissionSessionJpaRepositoryTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 驗證 Spring Data JPA 與實體映射對 H2 資料庫之 CRUD 與自訂查詢方法；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
@SpringBootTest
@Transactional
class TransmissionSessionJpaRepositoryTest {

    @Autowired
    private TransmissionSessionJpaRepository repository;

    /**
     * Feature: 傳輸階段資料庫持久化
     * Scenario: 成功保存並查詢傳輸階段實體
     * Given: 準備 TransmissionSessionJpaEntity 實體
     * When: 透過 repository 保存實體並依主鍵查詢
     * Then: 正確查得且各欄位值完全相符
     */
    @Test
    @DisplayName("情境：保存傳輸階段實體並依主鍵成功查詢")
    void testSaveAndFindById() {
        TransmissionSessionEntity entity = new TransmissionSessionEntity(
                "session-001",
                "SFTP",
                TransmissionStatus.COMPLETED,
                "/local/data.txt",
                "/remote/data.txt",
                "data.txt",
                1024L,
                150L,
                null,
                Instant.now(),
                Instant.now()
        );

        repository.save(entity);

        Optional<TransmissionSessionEntity> found = repository.findById("session-001");
        assertTrue(found.isPresent());
        assertEquals("session-001", found.get().getId());
        assertEquals("SFTP", found.get().getProtocol());
        assertEquals(TransmissionStatus.COMPLETED, found.get().getStatus());
        assertEquals("data.txt", found.get().getFileName());
        assertEquals(1024L, found.get().getFileSizeBytes());
        assertEquals(150L, found.get().getDurationMillis());
    }

    /**
     * Feature: 傳輸階段自訂條件查詢
     * Scenario: 依狀態與協定過濾傳輸階段記錄
     * Given: 保存數筆不同狀態與協定的傳輸實體
     * When: 呼叫 findByStatus 與 findByProtocol 查詢
     * Then: 正確過濾並回傳符合條件之紀錄清單
     */
    @Test
    @DisplayName("情境：依狀態與傳輸協定過濾查詢")
    void testFindByStatusAndProtocol() {
        TransmissionSessionEntity e1 = new TransmissionSessionEntity(
                "session-101",
                "FTP",
                TransmissionStatus.COMPLETED,
                null, null, "f1.txt", 100L, 20L, null, Instant.now(), Instant.now()
        );
        TransmissionSessionEntity e2 = new TransmissionSessionEntity(
                "session-102",
                "FTP",
                TransmissionStatus.FAILED,
                null, null, "f2.txt", 200L, 50L, "連線中斷", Instant.now(), Instant.now()
        );
        TransmissionSessionEntity e3 = new TransmissionSessionEntity(
                "session-103",
                "SFTP",
                TransmissionStatus.COMPLETED,
                null, null, "f3.txt", 300L, 80L, null, Instant.now(), Instant.now()
        );

        repository.saveAll(List.of(e1, e2, e3));

        List<TransmissionSessionEntity> completedList = repository.findByStatus(TransmissionStatus.COMPLETED);
        assertEquals(2, completedList.size());

        List<TransmissionSessionEntity> ftpList = repository.findByProtocol("FTP");
        assertEquals(2, ftpList.size());

        List<TransmissionSessionEntity> failedList = repository.findByStatus(TransmissionStatus.FAILED);
        assertEquals(1, failedList.size());
        assertEquals("連線中斷", failedList.get(0).getErrorMessage());
    }
}
