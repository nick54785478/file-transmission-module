package com.example.demo.presentation.controller;

import com.example.demo.application.port.in.CreateServerCredentialUseCase;
import com.example.demo.application.port.in.DeleteServerCredentialUseCase;
import com.example.demo.application.port.in.GetServerCredentialUseCase;
import com.example.demo.application.port.in.ListServerCredentialsUseCase;
import com.example.demo.application.port.in.ReactivateServerCredentialUseCase;
import com.example.demo.application.port.in.UpdateServerCredentialUseCase;
import com.example.demo.application.shared.command.CreateServerCredentialCommand;
import com.example.demo.application.shared.command.DeleteServerCredentialCommand;
import com.example.demo.application.shared.command.ReactivateServerCredentialCommand;
import com.example.demo.application.shared.command.UpdateServerCredentialCommand;
import com.example.demo.application.shared.dto.ServerCredentialGottenResult;
import com.example.demo.application.shared.query.GetServerCredentialQuery;
import com.example.demo.application.shared.query.ListServerCredentialsQuery;
import com.example.demo.presentation.assembler.ServerCredentialResourceAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 伺服器傳輸憑證 REST 控制器單元測試 (ServerCredentialControllerTest)
 * <p>
 * 遵循 AGENTS.md 測試規範，採用 BDD 與 Gherkin 語法標註業務情境。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ServerCredentialControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CreateServerCredentialUseCase createServerCredentialUseCase;

    @Mock
    private UpdateServerCredentialUseCase updateServerCredentialUseCase;

    @Mock
    private GetServerCredentialUseCase getServerCredentialUseCase;

    @Mock
    private ListServerCredentialsUseCase listServerCredentialsUseCase;

    @Mock
    private DeleteServerCredentialUseCase deleteServerCredentialUseCase;

    @Mock
    private ReactivateServerCredentialUseCase reactivateServerCredentialUseCase;

    @BeforeEach
    void setUp() {
        ServerCredentialResourceAssembler resourceAssembler = new ServerCredentialResourceAssembler();
        ServerCredentialController controller = new ServerCredentialController(
                createServerCredentialUseCase,
                updateServerCredentialUseCase,
                getServerCredentialUseCase,
                listServerCredentialsUseCase,
                deleteServerCredentialUseCase,
                reactivateServerCredentialUseCase,
                resourceAssembler
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /**
     * Feature: 伺服器憑證 REST API 控制器
     * Scenario: POST /api/credentials 建立憑證成功回傳 201 Created
     * Given: 傳入合法之憑證 JSON Payload
     * When: 發送 POST 請求至 /api/credentials
     * Then: 控制器呼叫 CreateServerCredentialUseCase 並回傳 201 Created 與脫敏資源
     */
    @Test
    @DisplayName("情境：POST /api/credentials 建立憑證成功回傳 201")
    void testCreateCredentialEndpointSuccess() throws Exception {
        ServerCredentialGottenResult mockResult = new ServerCredentialGottenResult(
                "cred-888",
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
                "本機 Docker SFTP",
                Instant.now(),
                Instant.now()
        );

        when(createServerCredentialUseCase.createCredential(any(CreateServerCredentialCommand.class)))
                .thenReturn(mockResult);

        String json = """
                {
                    "alias": "mock-sftp",
                    "host": "127.0.0.1",
                    "port": 2222,
                    "protocol": "SFTP",
                    "username": "tester",
                    "password": "password",
                    "description": "本機 Docker SFTP"
                }
                """;

        mockMvc.perform(post("/api/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("cred-888"))
                .andExpect(jsonPath("$.alias").value("mock-sftp"))
                .andExpect(jsonPath("$.maskedPassword").value("******"))
                .andExpect(jsonPath("$.hasPassword").value(true));
    }

    /**
     * Feature: 伺服器憑證 REST API 控制器
     * Scenario: PUT /api/credentials/{id} 更新憑證成功回傳 200 OK
     * Given: 傳入合法更新 JSON Payload
     * When: 發送 PUT 請求至 /api/credentials/{id}
     * Then: 控制器呼叫 UpdateServerCredentialUseCase 並回傳 200 OK
     */
    @Test
    @DisplayName("情境：PUT /api/credentials/{id} 更新憑證成功回傳 200")
    void testUpdateCredentialEndpointSuccess() throws Exception {
        ServerCredentialGottenResult updatedResult = new ServerCredentialGottenResult(
                "cred-888",
                "mock-sftp-v2",
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
                "更新後描述",
                Instant.now(),
                Instant.now()
        );

        when(updateServerCredentialUseCase.updateCredential(any(UpdateServerCredentialCommand.class)))
                .thenReturn(updatedResult);

        String json = """
                {
                    "alias": "mock-sftp-v2",
                    "description": "更新後描述"
                }
                """;

        mockMvc.perform(put("/api/credentials/cred-888")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("mock-sftp-v2"))
                .andExpect(jsonPath("$.description").value("更新後描述"));
    }

    /**
     * Feature: 伺服器憑證 REST API 控制器
     * Scenario: GET /api/credentials/{id} 依 ID 查詢憑證成功回傳 200 OK
     * Given: 傳入有效之 ID
     * When: 發送 GET 請求至 /api/credentials/{id}
     * Then: 控制器呼叫 GetServerCredentialUseCase 並回傳 200 OK 與脫敏資源
     */
    @Test
    @DisplayName("情境：GET /api/credentials/{id} 依 ID 查詢憑證成功回傳 200")
    void testGetCredentialByIdEndpointSuccess() throws Exception {
        ServerCredentialGottenResult mockResult = new ServerCredentialGottenResult(
                "cred-888",
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
                "本機 Docker SFTP",
                Instant.now(),
                Instant.now()
        );

        when(getServerCredentialUseCase.getCredential(any(GetServerCredentialQuery.class)))
                .thenReturn(mockResult);

        mockMvc.perform(get("/api/credentials/cred-888"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cred-888"))
                .andExpect(jsonPath("$.alias").value("mock-sftp"));
    }

    /**
     * Feature: 伺服器憑證 REST API 控制器
     * Scenario: GET /api/credentials/alias/{alias} 依別名查詢憑證成功回傳 200 OK
     * Given: 傳入有效之別名
     * When: 發送 GET 請求至 /api/credentials/alias/{alias}
     * Then: 控制器呼叫 GetServerCredentialUseCase 並回傳 200 OK
     */
    @Test
    @DisplayName("情境：GET /api/credentials/alias/{alias} 依別名查詢憑證成功回傳 200")
    void testGetCredentialByAliasEndpointSuccess() throws Exception {
        ServerCredentialGottenResult mockResult = new ServerCredentialGottenResult(
                "cred-888",
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
                "本機 Docker SFTP",
                Instant.now(),
                Instant.now()
        );

        when(getServerCredentialUseCase.getCredential(any(GetServerCredentialQuery.class)))
                .thenReturn(mockResult);

        mockMvc.perform(get("/api/credentials/alias/mock-sftp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("mock-sftp"));
    }

    /**
     * Feature: 伺服器憑證 REST API 控制器
     * Scenario: GET /api/credentials 查詢憑證列表成功回傳 200 OK
     * Given: 無傳入參數或傳入 keyword
     * When: 發送 GET 請求至 /api/credentials
     * Then: 控制器呼叫 ListServerCredentialsUseCase 並回傳 200 OK 與列表
     */
    @Test
    @DisplayName("情境：GET /api/credentials 查詢憑證列表成功回傳 200")
    void testListCredentialsEndpointSuccess() throws Exception {
        ServerCredentialGottenResult cred = new ServerCredentialGottenResult(
                "cred-1", "alias-1", "127.0.0.1", 2222, "SFTP", "tester",
                "******", null, null, true, false, false, true, "備註", Instant.now(), Instant.now()
        );

        when(listServerCredentialsUseCase.listCredentials(any(ListServerCredentialsQuery.class)))
                .thenReturn(List.of(cred));

        mockMvc.perform(get("/api/credentials?keyword=alias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alias").value("alias-1"));
    }

    /**
     * Feature: 伺服器憑證 REST API 控制器
     * Scenario: GET /api/credentials 支援依 active 狀態過濾查詢憑證
     * Given: 傳入 active=false 查詢參數
     * When: 發送 GET 請求至 /api/credentials?active=false
     * Then: 控制器構造包含 active=false 之 ListServerCredentialsQuery 並回傳 200 OK
     */
    @Test
    @DisplayName("情境：GET /api/credentials 帶 active 參數過濾軟刪除/停用憑證成功回傳 200")
    void testListCredentialsWithActiveFilterEndpointSuccess() throws Exception {
        ServerCredentialGottenResult inactiveCred = new ServerCredentialGottenResult(
                "cred-2", "inactive-alias", "127.0.0.1", 2222, "SFTP", "tester",
                "******", null, null, true, false, false, false, "已軟刪除憑證", Instant.now(), Instant.now()
        );

        when(listServerCredentialsUseCase.listCredentials(any(ListServerCredentialsQuery.class)))
                .thenReturn(List.of(inactiveCred));

        mockMvc.perform(get("/api/credentials?active=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alias").value("inactive-alias"))
                .andExpect(jsonPath("$[0].active").value(false));
    }

    /**
     * Feature: 伺服器憑證 REST API 控制器
     * Scenario: DELETE /api/credentials/{id} 軟刪除憑證成功回傳 204 No Content
     * Given: 傳入欲軟刪除之憑證 ID
     * When: 發送 DELETE 請求至 /api/credentials/{id}
     * Then: 控制器呼叫 DeleteServerCredentialUseCase 並回傳 204 No Content
     */
    @Test
    @DisplayName("情境：DELETE /api/credentials/{id} 軟刪除憑證成功回傳 204")
    void testDeleteCredentialEndpointSuccess() throws Exception {
        mockMvc.perform(delete("/api/credentials/cred-to-delete"))
                .andExpect(status().isNoContent());

        verify(deleteServerCredentialUseCase).deleteCredential(any(DeleteServerCredentialCommand.class));
    }

    /**
     * Feature: 伺服器憑證 REST API 控制器
     * Scenario: PUT /api/credentials/{id}/reactivate 重新生效憑證成功回傳 200 OK
     * Given: 傳入欲重新生效之憑證 ID
     * When: 發送 PUT 請求至 /api/credentials/{id}/reactivate
     * Then: 控制器呼叫 ReactivateServerCredentialUseCase 並回傳 200 OK 與 active=true 之脫敏資源
     */
    @Test
    @DisplayName("情境：PUT /api/credentials/{id}/reactivate 重新生效憑證成功回傳 200")
    void testReactivateCredentialPutEndpointSuccess() throws Exception {
        ServerCredentialGottenResult reactivated = new ServerCredentialGottenResult(
                "cred-to-reactivate", "sftp-active", "127.0.0.1", 2222, "SFTP", "tester",
                "******", null, null, true, false, false, true, "已重新生效", Instant.now(), Instant.now()
        );

        when(reactivateServerCredentialUseCase.reactivateCredential(any(ReactivateServerCredentialCommand.class)))
                .thenReturn(reactivated);

        mockMvc.perform(put("/api/credentials/cred-to-reactivate/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cred-to-reactivate"))
                .andExpect(jsonPath("$.alias").value("sftp-active"))
                .andExpect(jsonPath("$.active").value(true));

        verify(reactivateServerCredentialUseCase).reactivateCredential(any(ReactivateServerCredentialCommand.class));
    }

    /**
     * Feature: 伺服器憑證 REST API 控制器
     * Scenario: POST /api/credentials/{id}/reactivate 重新生效憑證成功回傳 200 OK
     * Given: 傳入欲重新生效之憑證 ID
     * When: 發送 POST 請求至 /api/credentials/{id}/reactivate
     * Then: 控制器呼叫 ReactivateServerCredentialUseCase 並回傳 200 OK 與 active=true 之脫敏資源
     */
    @Test
    @DisplayName("情境：POST /api/credentials/{id}/reactivate 重新生效憑證成功回傳 200")
    void testReactivateCredentialPostEndpointSuccess() throws Exception {
        ServerCredentialGottenResult reactivated = new ServerCredentialGottenResult(
                "cred-to-reactivate", "sftp-active", "127.0.0.1", 2222, "SFTP", "tester",
                "******", null, null, true, false, false, true, "已重新生效", Instant.now(), Instant.now()
        );

        when(reactivateServerCredentialUseCase.reactivateCredential(any(ReactivateServerCredentialCommand.class)))
                .thenReturn(reactivated);

        mockMvc.perform(post("/api/credentials/cred-to-reactivate/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cred-to-reactivate"))
                .andExpect(jsonPath("$.alias").value("sftp-active"))
                .andExpect(jsonPath("$.active").value(true));

        verify(reactivateServerCredentialUseCase).reactivateCredential(any(ReactivateServerCredentialCommand.class));
    }
}
