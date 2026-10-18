package com.example.demo.presentation.controller;

import com.example.demo.application.port.in.CreateServerCredentialUseCase;
import com.example.demo.application.port.in.DeleteServerCredentialUseCase;
import com.example.demo.application.port.in.GetServerCredentialUseCase;
import com.example.demo.application.port.in.ListServerCredentialsUseCase;
import com.example.demo.application.port.in.UpdateServerCredentialUseCase;
import com.example.demo.application.shared.command.CreateServerCredentialCommand;
import com.example.demo.application.shared.command.DeleteServerCredentialCommand;
import com.example.demo.application.shared.command.UpdateServerCredentialCommand;
import com.example.demo.application.shared.dto.ServerCredentialGottenResult;
import com.example.demo.application.shared.query.GetServerCredentialQuery;
import com.example.demo.application.shared.query.ListServerCredentialsQuery;
import com.example.demo.presentation.assembler.ServerCredentialResourceAssembler;
import com.example.demo.presentation.resource.in.CreateServerCredentialResource;
import com.example.demo.presentation.resource.in.UpdateServerCredentialResource;
import com.example.demo.presentation.resource.out.ServerCredentialRetrievedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * 伺服器傳輸憑證維護 REST 控制器 (ServerCredentialController - Inbound Adapter)
 * <p>
 * <b>架構與設計職責說明 (AGENTS.md 2 與 3-9)：</b>
 * <ul>
 *   <li>位於表現層 <code>presentation/controller/</code>。</li>
 *   <li>提供主機連線帳密與 SSH 私鑰憑證的完整維護介面 (CRUD RESTful API)。</li>
 *   <li>嚴格遵守純資料載體原則，透過 {@link ServerCredentialResourceAssembler} 進行防腐層物件轉換。</li>
 * </ul>
 * </p>
 */
@Tag(name = "伺服器傳輸憑證管理 (Server Credentials)", description = "維護 SFTP / FTP / FTPS 主機連線帳號、密碼與 SSH 私鑰設定檔")
@RestController
@RequestMapping("/api/credentials")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ServerCredentialController {

    private final CreateServerCredentialUseCase createServerCredentialUseCase;
    private final UpdateServerCredentialUseCase updateServerCredentialUseCase;
    private final GetServerCredentialUseCase getServerCredentialUseCase;
    private final ListServerCredentialsUseCase listServerCredentialsUseCase;
    private final DeleteServerCredentialUseCase deleteServerCredentialUseCase;
    private final ServerCredentialResourceAssembler resourceAssembler;

    public ServerCredentialController(CreateServerCredentialUseCase createServerCredentialUseCase,
                                     UpdateServerCredentialUseCase updateServerCredentialUseCase,
                                     GetServerCredentialUseCase getServerCredentialUseCase,
                                     ListServerCredentialsUseCase listServerCredentialsUseCase,
                                     DeleteServerCredentialUseCase deleteServerCredentialUseCase,
                                     ServerCredentialResourceAssembler resourceAssembler) {
        this.createServerCredentialUseCase = Objects.requireNonNull(createServerCredentialUseCase, "createServerCredentialUseCase 不能為空");
        this.updateServerCredentialUseCase = Objects.requireNonNull(updateServerCredentialUseCase, "updateServerCredentialUseCase 不能為空");
        this.getServerCredentialUseCase = Objects.requireNonNull(getServerCredentialUseCase, "getServerCredentialUseCase 不能為空");
        this.listServerCredentialsUseCase = Objects.requireNonNull(listServerCredentialsUseCase, "listServerCredentialsUseCase 不能為空");
        this.deleteServerCredentialUseCase = Objects.requireNonNull(deleteServerCredentialUseCase, "deleteServerCredentialUseCase 不能為空");
        this.resourceAssembler = Objects.requireNonNull(resourceAssembler, "resourceAssembler 不能為空");
    }

    @Operation(summary = "建立全新伺服器連線憑證", description = "註冊主機連線帳號密碼或 SSH 私鑰，後端自動以 AES-GCM 密文落盤保存")
    @PostMapping
    public ResponseEntity<ServerCredentialRetrievedResource> createCredential(
            @RequestBody CreateServerCredentialResource resource) {
        CreateServerCredentialCommand command = resourceAssembler.toCreateCommand(resource);
        ServerCredentialGottenResult result = createServerCredentialUseCase.createCredential(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(resourceAssembler.toRetrievedResource(result));
    }

    @Operation(summary = "更新既有伺服器連線憑證", description = "依憑證 ID 更新主機連線資訊、帳密或私鑰")
    @PutMapping("/{id}")
    public ResponseEntity<ServerCredentialRetrievedResource> updateCredential(
            @Parameter(description = "憑證唯一識別碼") @PathVariable String id,
            @RequestBody UpdateServerCredentialResource resource) {
        UpdateServerCredentialCommand command = resourceAssembler.toUpdateCommand(id, resource);
        ServerCredentialGottenResult result = updateServerCredentialUseCase.updateCredential(command);
        return ResponseEntity.ok(resourceAssembler.toRetrievedResource(result));
    }

    @Operation(summary = "依 ID 查詢憑證詳細資料", description = "取得憑證資訊 (密碼與私鑰已進行脫敏遮罩)")
    @GetMapping("/{id}")
    public ResponseEntity<ServerCredentialRetrievedResource> getCredentialById(
            @Parameter(description = "憑證唯一識別碼") @PathVariable String id) {
        GetServerCredentialQuery query = GetServerCredentialQuery.byId(id);
        ServerCredentialGottenResult result = getServerCredentialUseCase.getCredential(query);
        return ResponseEntity.ok(resourceAssembler.toRetrievedResource(result));
    }

    @Operation(summary = "依別名 (Alias) 查詢憑證詳細資料", description = "以易讀別名快速定位伺服器連線憑證")
    @GetMapping("/alias/{alias}")
    public ResponseEntity<ServerCredentialRetrievedResource> getCredentialByAlias(
            @Parameter(description = "憑證別名") @PathVariable String alias) {
        GetServerCredentialQuery query = GetServerCredentialQuery.byAlias(alias);
        ServerCredentialGottenResult result = getServerCredentialUseCase.getCredential(query);
        return ResponseEntity.ok(resourceAssembler.toRetrievedResource(result));
    }

    @Operation(summary = "查詢所有憑證或依關鍵字搜尋", description = "列出所有已註冊憑證；可傳入 keyword 模糊搜尋別名、主機或使用者名稱")
    @GetMapping
    public ResponseEntity<List<ServerCredentialRetrievedResource>> listCredentials(
            @Parameter(description = "搜尋關鍵字 (選填)") @RequestParam(required = false) String keyword) {
        ListServerCredentialsQuery query = new ListServerCredentialsQuery(keyword);
        List<ServerCredentialGottenResult> results = listServerCredentialsUseCase.listCredentials(query);
        return ResponseEntity.ok(resourceAssembler.toRetrievedResourceList(results));
    }

    @Operation(summary = "依 ID 刪除伺服器憑證", description = "自系統中永久刪除指定之連線憑證")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCredential(
            @Parameter(description = "憑證唯一識別碼") @PathVariable String id) {
        DeleteServerCredentialCommand command = new DeleteServerCredentialCommand(id);
        deleteServerCredentialUseCase.deleteCredential(command);
        return ResponseEntity.noContent().build();
    }
}
