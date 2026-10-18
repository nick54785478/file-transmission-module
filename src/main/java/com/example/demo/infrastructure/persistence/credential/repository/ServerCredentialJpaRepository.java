package com.example.demo.infrastructure.persistence.credential.repository;

import com.example.demo.infrastructure.persistence.credential.entity.ServerCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 伺服器傳輸憑證 Spring Data JPA 儲存庫 (ServerCredentialJpaRepository - Infrastructure Layer)
 * <p>
 * <b>架構與設計職責說明：</b>
 * <ul>
 *   <li>位於基礎設施持久化層 <code>infrastructure/persistence/credential/repository/</code>。</li>
 *   <li>繼承 {@link JpaRepository}，提供基本的 CRUD 與分頁查詢。</li>
 *   <li>提供依別名 (Alias)、帳號 (Username) 與關鍵字模糊搜尋查詢方法。</li>
 * </ul>
 * </p>
 */
@Repository
public interface ServerCredentialJpaRepository extends JpaRepository<ServerCredentialEntity, String> {

    /**
     * 依唯一別名查詢憑證實體
     *
     * @param alias 憑證別名
     * @return 憑證實體 Optional
     */
    Optional<ServerCredentialEntity> findByAlias(String alias);

    /**
     * 檢查指定別名是否已存在
     *
     * @param alias 憑證別名
     * @return 若已存在回傳 true，否則回傳 false
     */
    boolean existsByAlias(String alias);

    /**
     * 依登入使用者名稱查詢憑證列表
     *
     * @param username 登入使用者名稱
     * @return 憑證實體列表
     */
    List<ServerCredentialEntity> findByUsername(String username);

    /**
     * 依關鍵字模糊搜尋憑證 (匹配別名、帳號、主機名稱或描述)
     *
     * @param keyword 搜尋關鍵字
     * @return 匹配之憑證實體列表
     */
    @Query("SELECT c FROM ServerCredentialEntity c WHERE " +
           "LOWER(c.alias) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "(c.host IS NOT NULL AND LOWER(c.host) LIKE LOWER(CONCAT('%', :keyword, '%'))) OR " +
           "(c.description IS NOT NULL AND LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<ServerCredentialEntity> searchByKeyword(@Param("keyword") String keyword);
}
