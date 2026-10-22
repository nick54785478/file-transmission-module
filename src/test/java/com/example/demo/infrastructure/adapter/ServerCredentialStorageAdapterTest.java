package com.example.demo.infrastructure.adapter;

import com.example.demo.application.shared.command.CreateServerCredentialCommand;
import com.example.demo.application.shared.command.UpdateServerCredentialCommand;
import com.example.demo.application.shared.dto.ServerCredentialGottenResult;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.example.demo.infrastructure.persistence.credential.entity.ServerCredentialEntity;
import com.example.demo.infrastructure.persistence.credential.repository.ServerCredentialJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 伺服器傳輸憑證儲存適配器單元測試 (ServerCredentialStorageAdapterTest)
 * <p>
 * 遵循 AGENTS.md 測試規範，採用 BDD 與 Gherkin 語法驗證 AES-GCM 機密落盤加密與還原解析。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ServerCredentialStorageAdapterTest {

    @Mock
    private ServerCredentialJpaRepository serverCredentialJpaRepository;

    private ServerCredentialStorageAdapter storageAdapter;

    @BeforeEach
    void setUp() {
        storageAdapter = new ServerCredentialStorageAdapter(serverCredentialJpaRepository);
    }

    /**
     * Feature: 憑證持久化落盤
     * Scenario: 建立憑證時直接將 Command 中的機密資料持久化落盤
     * Given: 傳入已加密之 CreateServerCredentialCommand
     * When: 呼叫 save 方法
     * Then: 適配器直接將 Command 中的值寫入 JPA 實體並儲存
     */
    @Test
    @DisplayName("情境：建立憑證時直接將機密資料寫入 JPA 實體落盤")
    void testSaveStoresSecretsDirectly() {
        CreateServerCredentialCommand command = new CreateServerCredentialCommand(
                "my-sftp",
                "192.168.1.100",
                22,
                "SFTP",
                "user1",
                "ENC(aes_encrypted_password)",
                "ENC(aes_encrypted_key)",
                "ENC(aes_encrypted_passphrase)",
                "測試憑證"
        );

        when(serverCredentialJpaRepository.existsByAlias("my-sftp")).thenReturn(false);
        when(serverCredentialJpaRepository.save(any(ServerCredentialEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ServerCredentialGottenResult result = storageAdapter.save(command);

        assertThat(result).isNotNull();
        assertThat(result.alias()).isEqualTo("my-sftp");
        assertThat(result.maskedPassword()).isEqualTo("******");
        assertThat(result.hasPassword()).isTrue();
        assertThat(result.hasPrivateKey()).isTrue();

        ArgumentCaptor<ServerCredentialEntity> captor = ArgumentCaptor.forClass(ServerCredentialEntity.class);
        verify(serverCredentialJpaRepository).save(captor.capture());
        ServerCredentialEntity savedEntity = captor.getValue();

        assertThat(savedEntity.getEncryptedPassword()).isEqualTo("ENC(aes_encrypted_password)");
        assertThat(savedEntity.getEncryptedPrivateKey()).isEqualTo("ENC(aes_encrypted_key)");
        assertThat(savedEntity.getEncryptedPassphrase()).isEqualTo("ENC(aes_encrypted_passphrase)");
    }

    /**
     * Feature: 憑證持久化與連線配置解析
     * Scenario: 傳輸任務依別名解析出封裝機密之 TransmissionServerConfig
     * Given: 資料庫中已存在憑證實體
     * When: 呼叫 resolveTransmissionConfig("my-sftp")
     * Then: 適配器封裝主機資訊與機密憑證為 TransmissionServerConfig 物件
     */
    @Test
    @DisplayName("情境：傳輸任務解析憑證時封裝連線配置物件")
    void testResolveTransmissionConfigBuildsServerConfig() {
        ServerCredentialEntity mockEntity = new ServerCredentialEntity(
                "cred-999",
                "my-sftp",
                "127.0.0.1",
                2222,
                "SFTP",
                "tester",
                "ENC(aes_enc_pass)",
                "ENC(aes_enc_key)",
                null,
                "測試"
        );

        when(serverCredentialJpaRepository.findById("my-sftp")).thenReturn(Optional.empty());
        when(serverCredentialJpaRepository.findByAlias("my-sftp")).thenReturn(Optional.of(mockEntity));

        Optional<TransmissionServerConfig> configOpt = storageAdapter.resolveTransmissionConfig("my-sftp");

        assertThat(configOpt).isPresent();
        TransmissionServerConfig config = configOpt.get();
        assertThat(config.getHost()).isEqualTo("127.0.0.1");
        assertThat(config.getPort()).isEqualTo(2222);
        assertThat(config.getProtocol()).isEqualTo(TransmissionProtocol.SFTP);
        assertThat(config.getUsername()).isEqualTo("tester");
        assertThat(config.getPassword()).isEqualTo("ENC(aes_enc_pass)");
        assertThat(config.getPrivateKey()).isEqualTo("ENC(aes_enc_key)");
    }

    /**
     * Feature: 憑證持久化與軟刪除
     * Scenario: 刪除憑證時執行軟刪除將 active 狀態標記為 false
     * Given: 資料庫中存在處於啟用狀態之憑證實體 (active = true)
     * When: 呼叫 deleteById("cred-999") 業務方法
     * Then: 實體狀態被標記為停用 (active = false)，保存至 repository，且不執行物理刪除
     */
    @Test
    @DisplayName("情境：刪除憑證時執行軟刪除將 active 狀態更新為 false")
    void testDeleteByIdPerformsSoftDelete() {
        ServerCredentialEntity mockEntity = new ServerCredentialEntity(
                "cred-999",
                "my-sftp",
                "127.0.0.1",
                2222,
                "SFTP",
                "tester",
                "ENC(pass)",
                null,
                null,
                "測試"
        );
        assertThat(mockEntity.isActive()).isTrue();

        when(serverCredentialJpaRepository.findById("cred-999")).thenReturn(Optional.of(mockEntity));
        when(serverCredentialJpaRepository.save(any(ServerCredentialEntity.class))).thenAnswer(i -> i.getArgument(0));

        storageAdapter.deleteById("cred-999");

        assertThat(mockEntity.isActive()).isFalse();
        verify(serverCredentialJpaRepository).save(mockEntity);
    }

    /**
     * Feature: 憑證持久化與重生效
     * Scenario: 重新生效憑證時將 active 狀態標記為 true
     * Given: 資料庫中存在處於停用/軟刪除狀態之憑證實體 (active = false)
     * When: 呼叫 reactivateById("cred-999") 業務方法
     * Then: 實體狀態恢復為啟用 (active = true)，保存至 repository 並回傳 active = true 之 DTO
     */
    @Test
    @DisplayName("情境：重生效憑證時將 active 狀態更新為 true 並回傳有效結果")
    void testReactivateByIdSuccess() {
        ServerCredentialEntity mockEntity = new ServerCredentialEntity(
                "cred-999",
                "my-sftp",
                "127.0.0.1",
                2222,
                "SFTP",
                "tester",
                "ENC(pass)",
                null,
                null,
                "測試"
        );
        mockEntity.deactivate();
        assertThat(mockEntity.isActive()).isFalse();

        when(serverCredentialJpaRepository.findById("cred-999")).thenReturn(Optional.of(mockEntity));
        when(serverCredentialJpaRepository.save(any(ServerCredentialEntity.class))).thenAnswer(i -> i.getArgument(0));

        ServerCredentialGottenResult result = storageAdapter.reactivateById("cred-999");

        assertThat(result).isNotNull();
        assertThat(result.active()).isTrue();
        assertThat(mockEntity.isActive()).isTrue();
        verify(serverCredentialJpaRepository).save(mockEntity);
    }

    /**
     * Feature: 憑證持久化與更新校驗
     * Scenario: 更新憑證別名時若新別名已被其他憑證使用則拋出例外
     * Given: 資料庫中存在原憑證 (id = "cred-1", alias = "orig-alias")，且欲變更為 "duplicate-alias"
     * When: repository 判斷 "duplicate-alias" 已存在並呼叫 update 方法
     * Then: 拋出 IllegalArgumentException 並提示憑證別名已存在
     */
    @Test
    @DisplayName("情境：更新憑證時若新別名重複則拋出 IllegalArgumentException")
    void testUpdateThrowsWhenAliasAlreadyExists() {
        ServerCredentialEntity mockEntity = new ServerCredentialEntity(
                "cred-1",
                "orig-alias",
                "127.0.0.1",
                22,
                "SFTP",
                "user",
                "ENC(pass)",
                null,
                null,
                "desc"
        );

        UpdateServerCredentialCommand command = new UpdateServerCredentialCommand(
                "cred-1",
                "duplicate-alias",
                "127.0.0.1",
                22,
                "SFTP",
                "user",
                null,
                null,
                null,
                "desc",
                true
        );

        when(serverCredentialJpaRepository.findById("cred-1")).thenReturn(Optional.of(mockEntity));
        when(serverCredentialJpaRepository.existsByAlias("duplicate-alias")).thenReturn(true);

        assertThatThrownBy(() -> storageAdapter.update(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("憑證別名已存在，不可重複使用: duplicate-alias");
    }

    /**
     * Feature: 憑證持久化與全量查詢
     * Scenario: 查詢所有憑證時正確映射並脫敏敏感機密
     * Given: 資料庫中存在啟用與加密之憑證實體
     * When: 呼叫 findAll() 業務方法
     * Then: 正確回傳脫敏後之憑證 DTO 清單
     */
    @Test
    @DisplayName("情境：查詢所有憑證時正確回傳脫敏結果清單")
    void testFindAllSuccess() {
        ServerCredentialEntity entity = new ServerCredentialEntity(
                "cred-100",
                "prod-sftp",
                "10.0.0.1",
                22,
                "SFTP",
                "admin",
                "ENC(pass)",
                null,
                null,
                "Prod Server"
        );
        when(serverCredentialJpaRepository.findAll()).thenReturn(List.of(entity));

        List<ServerCredentialGottenResult> results = storageAdapter.findAll();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).alias()).isEqualTo("prod-sftp");
        assertThat(results.get(0).maskedPassword()).isEqualTo("******");
        verify(serverCredentialJpaRepository).findAll();
    }

    /**
     * Feature: 憑證持久化與關鍵字搜尋
     * Scenario: 關鍵字為空或空白時回退查詢全部憑證
     * Given: 搜尋參數為 null 或空白字串
     * When: 呼叫 search(null) 或 search("   ")
     * Then: 委派內部全量查詢並回傳完整憑證清單
     */
    @Test
    @DisplayName("情境：搜尋關鍵字為空或空白時回退查詢所有憑證")
    void testSearchWithBlankKeywordDelegatesToFindAll() {
        ServerCredentialEntity entity = new ServerCredentialEntity(
                "cred-101",
                "backup-ftp",
                "10.0.0.2",
                21,
                "FTP",
                "ftpuser",
                "ENC(pass)",
                null,
                null,
                "Backup Server"
        );
        when(serverCredentialJpaRepository.findAll()).thenReturn(List.of(entity));

        List<ServerCredentialGottenResult> nullResults = storageAdapter.search(null);
        List<ServerCredentialGottenResult> emptyResults = storageAdapter.search("   ");

        assertThat(nullResults).hasSize(1);
        assertThat(emptyResults).hasSize(1);
        verify(serverCredentialJpaRepository, org.mockito.Mockito.times(2)).findAll();
    }

    /**
     * Feature: 憑證持久化與關鍵字搜尋
     * Scenario: 傳入有效關鍵字時根據關鍵字過濾憑證
     * Given: 搜尋關鍵字為 "sftp"
     * When: 呼叫 search(" sftp ")
     * Then: 去除頭尾空格後調用 searchByKeyword 查詢並回傳符合之憑證清單
     */
    @Test
    @DisplayName("情境：傳入有效關鍵字時根據關鍵字調用 JPA searchByKeyword 查詢")
    void testSearchWithKeywordSuccess() {
        ServerCredentialEntity entity = new ServerCredentialEntity(
                "cred-102",
                "sftp-cluster",
                "10.0.0.3",
                22,
                "SFTP",
                "cluster_admin",
                "ENC(pass)",
                null,
                null,
                "SFTP Cluster"
        );
        when(serverCredentialJpaRepository.searchByKeyword("sftp")).thenReturn(List.of(entity));

        List<ServerCredentialGottenResult> results = storageAdapter.search(" sftp ");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).alias()).isEqualTo("sftp-cluster");
        verify(serverCredentialJpaRepository).searchByKeyword("sftp");
    }
}
