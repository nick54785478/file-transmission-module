package com.example.demo.application.service;

import com.example.demo.application.port.out.ServerCredentialStoragePort;
import com.example.demo.application.shared.dto.ServerCredentialGottenResult;
import com.example.demo.application.shared.query.GetServerCredentialQuery;
import com.example.demo.application.shared.query.ListServerCredentialsQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 伺服器傳輸憑證查詢服務單元測試 (ServerCredentialQueryServiceTest)
 * <p>
 * 遵循 AGENTS.md 測試規範，採用 BDD 與 Gherkin 語法標註業務情境。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ServerCredentialQueryServiceTest {

    @Mock
    private ServerCredentialStoragePort serverCredentialStoragePort;

    private ServerCredentialQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new ServerCredentialQueryService(serverCredentialStoragePort);
    }

    /**
     * Feature: 伺服器傳輸憑證查詢
     * Scenario: 依憑證 ID 查詢成功
     * Given: 傳入有效之 ID
     * When: 呼叫 getCredential 業務方法
     * Then: 回傳對應之 ServerCredentialGottenResult
     */
    @Test
    @DisplayName("情境：依 ID 成功查詢指定憑證")
    void testGetCredentialByIdSuccess() {
        GetServerCredentialQuery query = GetServerCredentialQuery.byId("cred-123");

        ServerCredentialGottenResult mockResult = new ServerCredentialGottenResult(
                "cred-123",
                "mock-sftp",
                "127.0.0.1",
                2222,
                "SFTP",
                "tester",
                "******",
                null,
                null,
                true,
                false,
                false,
                true,
                "測試憑證",
                Instant.now(),
                Instant.now()
        );

        when(serverCredentialStoragePort.findById("cred-123")).thenReturn(Optional.of(mockResult));

        ServerCredentialGottenResult actual = queryService.getCredential(query);

        assertThat(actual).isNotNull();
        assertThat(actual.id()).isEqualTo("cred-123");
        assertThat(actual.alias()).isEqualTo("mock-sftp");
        verify(serverCredentialStoragePort).findById("cred-123");
    }

    /**
     * Feature: 伺服器傳輸憑證查詢
     * Scenario: 依別名 (Alias) 查詢成功
     * Given: 傳入有效之 alias
     * When: 呼叫 getCredential 業務方法
     * Then: 回傳對應之 ServerCredentialGottenResult
     */
    @Test
    @DisplayName("情境：依 Alias 成功查詢指定憑證")
    void testGetCredentialByAliasSuccess() {
        GetServerCredentialQuery query = GetServerCredentialQuery.byAlias("mock-sftp");

        ServerCredentialGottenResult mockResult = new ServerCredentialGottenResult(
                "cred-123",
                "mock-sftp",
                "127.0.0.1",
                2222,
                "SFTP",
                "tester",
                "******",
                null,
                null,
                true,
                false,
                false,
                true,
                "測試憑證",
                Instant.now(),
                Instant.now()
        );

        when(serverCredentialStoragePort.findByAlias("mock-sftp")).thenReturn(Optional.of(mockResult));

        ServerCredentialGottenResult actual = queryService.getCredential(query);

        assertThat(actual).isNotNull();
        assertThat(actual.alias()).isEqualTo("mock-sftp");
        verify(serverCredentialStoragePort).findByAlias("mock-sftp");
    }

    /**
     * Feature: 伺服器傳輸憑證查詢
     * Scenario: 查詢不存在之憑證拋出 NoSuchElementException
     * Given: 傳入不存在之 ID
     * When: 呼叫 getCredential 業務方法
     * Then: 拋出 NoSuchElementException
     */
    @Test
    @DisplayName("情境：查詢不存在之憑證拋出例外")
    void testGetCredentialNotFoundThrowsException() {
        GetServerCredentialQuery query = GetServerCredentialQuery.byId("not-found-id");

        when(serverCredentialStoragePort.findById("not-found-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getCredential(query))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("查無指定的伺服器憑證");
    }

    /**
     * Feature: 伺服器傳輸憑證查詢
     * Scenario: 列出所有憑證列表
     * Given: 傳入全量查詢 Query
     * When: 呼叫 listCredentials 業務方法
     * Then: 回傳所有憑證 DTO 列表
     */
    @Test
    @DisplayName("情境：全量查詢憑證列表成功")
    void testListAllCredentialsSuccess() {
        ListServerCredentialsQuery query = ListServerCredentialsQuery.all();

        ServerCredentialGottenResult cred1 = new ServerCredentialGottenResult(
                "cred-1", "alias-1", "127.0.0.1", 2222, "SFTP", "user1",
                "******", null, null, true, false, false, true, "備註1", Instant.now(), Instant.now()
        );
        ServerCredentialGottenResult cred2 = new ServerCredentialGottenResult(
                "cred-2", "alias-2", "127.0.0.1", 2121, "FTP", "user2",
                "******", null, null, true, false, false, true, "備註2", Instant.now(), Instant.now()
        );

        when(serverCredentialStoragePort.findAll()).thenReturn(List.of(cred1, cred2));

        List<ServerCredentialGottenResult> actualList = queryService.listCredentials(query);

        assertThat(actualList).hasSize(2);
        assertThat(actualList).extracting(ServerCredentialGottenResult::alias)
                .containsExactly("alias-1", "alias-2");
        verify(serverCredentialStoragePort).findAll();
    }

    /**
     * Feature: 伺服器傳輸憑證查詢
     * Scenario: 依關鍵字模糊搜尋憑證列表
     * Given: 傳入包含 keyword 之查詢 Query
     * When: 呼叫 listCredentials 業務方法
     * Then: 委派 search 方法並回傳匹配之 DTO 列表
     */
    @Test
    @DisplayName("情境：依關鍵字模糊搜尋憑證成功")
    void testSearchCredentialsByKeywordSuccess() {
        ListServerCredentialsQuery query = ListServerCredentialsQuery.withKeyword("sftp");

        ServerCredentialGottenResult cred1 = new ServerCredentialGottenResult(
                "cred-1", "mock-sftp", "127.0.0.1", 2222, "SFTP", "tester",
                "******", null, null, true, false, false, true, "備註", Instant.now(), Instant.now()
        );

        when(serverCredentialStoragePort.search("sftp")).thenReturn(List.of(cred1));

        List<ServerCredentialGottenResult> actualList = queryService.listCredentials(query);

        assertThat(actualList).hasSize(1);
        assertThat(actualList.get(0).alias()).isEqualTo("mock-sftp");
        verify(serverCredentialStoragePort).search("sftp");
    }
}
