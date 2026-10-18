package com.example.demo.application.port.out;

import com.example.demo.application.shared.command.CreateServerCredentialCommand;
import com.example.demo.application.shared.command.UpdateServerCredentialCommand;
import com.example.demo.application.shared.dto.ServerCredentialGottenResult;
import com.example.demo.application.shared.vo.TransmissionServerConfig;

import java.util.List;
import java.util.Optional;

/**
 * 伺服器傳輸憑證儲存輸出埠 (ServerCredentialStoragePort - Outbound Port)
 * <p>
 * <b>六角形架構合約規範 (AGENTS.md 2 與 3-8)：</b>
 * <ul>
 *   <li>命名規範: <code>out : ... + Port</code>。</li>
 *   <li>位於 <code>application/port/out/</code>，代表應用層向基礎設施持久化層所要求的技術介面。</li>
 *   <li><b>方法傳參及回傳型別嚴禁使用任何技術類或環境相依物件 (如 ORM Entity)：</b>
 *       僅可接收並回傳 Command、Query、DTO ({@link ServerCredentialGottenResult}) 與數值物件 ({@link TransmissionServerConfig})。</li>
 * </ul>
 * </p>
 */
public interface ServerCredentialStoragePort {

    /**
     * 儲存全新伺服器憑證 (底層自動實施 AES-GCM 密文落盤)
     *
     * @param command 建立命令
     * @return 遮罩後之憑證查詢結果 DTO
     */
    ServerCredentialGottenResult save(CreateServerCredentialCommand command);

    /**
     * 更新既有伺服器憑證
     *
     * @param command 更新命令
     * @return 遮罩後之憑證查詢結果 DTO
     */
    ServerCredentialGottenResult update(UpdateServerCredentialCommand command);

    /**
     * 依唯一識別碼查詢憑證
     *
     * @param id 憑證 ID
     * @return 遮罩後之憑證 DTO Optional
     */
    Optional<ServerCredentialGottenResult> findById(String id);

    /**
     * 依別名查詢憑證
     *
     * @param alias 憑證別名
     * @return 遮罩後之憑證 DTO Optional
     */
    Optional<ServerCredentialGottenResult> findByAlias(String alias);

    /**
     * 依憑證 ID 或別名解析並解密為完整的傳輸伺服器配置數值物件
     *
     * @param idOrAlias 憑證 ID 或易讀別名
     * @return 包含解密後帳號密碼/私鑰之 {@link TransmissionServerConfig} Optional
     */
    Optional<TransmissionServerConfig> resolveTransmissionConfig(String idOrAlias);

    /**
     * 查詢所有憑證列表
     *
     * @return 憑證 DTO 列表
     */
    List<ServerCredentialGottenResult> findAll();

    /**
     * 依關鍵字搜尋憑證列表
     *
     * @param keyword 關鍵字
     * @return 憑證 DTO 列表
     */
    List<ServerCredentialGottenResult> search(String keyword);

    /**
     * 檢查別名是否存在
     *
     * @param alias 別名
     * @return 若存在回傳 true
     */
    boolean existsByAlias(String alias);

    /**
     * 依唯一識別碼或別名軟刪除憑證 (將 active 狀態標記為 false)
     *
     * @param id 憑證 ID 或別名
     */
    void deleteById(String id);

    /**
     * 依唯一識別碼或別名重新啟用/生效憑證 (將 active 狀態標記為 true)
     *
     * @param id 憑證 ID 或別名
     * @return 重新生效後之憑證查詢結果 DTO
     */
    ServerCredentialGottenResult reactivateById(String id);
}
