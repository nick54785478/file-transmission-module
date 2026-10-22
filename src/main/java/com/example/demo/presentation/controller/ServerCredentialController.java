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
import com.example.demo.presentation.resource.in.CreateServerCredentialResource;
import com.example.demo.presentation.resource.in.UpdateServerCredentialResource;
import com.example.demo.presentation.resource.out.ServerCredentialRetrievedResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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
 *   <li><b>防腐層職責：</b> 嚴格遵守純資料載體原則，透過 {@link ServerCredentialResourceAssembler} 進行防腐層物件轉換。</li>
 *   <li><b>防禦性輸入檢核：</b> 引入 Spring Validation (<code>@Validated</code>, <code>@Valid</code>, <code>@NotBlank</code>) 嚴格把關輸入參數。</li>
 *   <li><b>軟刪除與重生效機制：</b> 刪除 API 執行軟刪除 (標記為停用/失效狀態)，並提供 <code>/reactivate</code> 重生效配置介面。</li>
 * </ul>
 * </p>
 */
@Tag(name = "伺服器傳輸憑證管理 (Server Credentials)", description = "維護 SFTP / FTP / FTPS 主機連線帳號、密碼與 SSH 私鑰設定檔")
@RestController
@RequestMapping("/api/credentials")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Validated
public class ServerCredentialController {

    private final CreateServerCredentialUseCase createServerCredentialUseCase;
    private final UpdateServerCredentialUseCase updateServerCredentialUseCase;
    private final GetServerCredentialUseCase getServerCredentialUseCase;
    private final ListServerCredentialsUseCase listServerCredentialsUseCase;
    private final DeleteServerCredentialUseCase deleteServerCredentialUseCase;
    private final ReactivateServerCredentialUseCase reactivateServerCredentialUseCase;
    private final ServerCredentialResourceAssembler resourceAssembler;

    /**
     * 完整建構子：注入所有 UseCase 與 Assembler (包含重生效 UseCase)
     */
    @Autowired
    public ServerCredentialController(CreateServerCredentialUseCase createServerCredentialUseCase,
                                     UpdateServerCredentialUseCase updateServerCredentialUseCase,
                                     GetServerCredentialUseCase getServerCredentialUseCase,
                                     ListServerCredentialsUseCase listServerCredentialsUseCase,
                                     DeleteServerCredentialUseCase deleteServerCredentialUseCase,
                                     ReactivateServerCredentialUseCase reactivateServerCredentialUseCase,
                                     ServerCredentialResourceAssembler resourceAssembler) {
        this.createServerCredentialUseCase = Objects.requireNonNull(createServerCredentialUseCase, "createServerCredentialUseCase 不能為空");
        this.updateServerCredentialUseCase = Objects.requireNonNull(updateServerCredentialUseCase, "updateServerCredentialUseCase 不能為空");
        this.getServerCredentialUseCase = Objects.requireNonNull(getServerCredentialUseCase, "getServerCredentialUseCase 不能為空");
        this.listServerCredentialsUseCase = Objects.requireNonNull(listServerCredentialsUseCase, "listServerCredentialsUseCase 不能為空");
        this.deleteServerCredentialUseCase = Objects.requireNonNull(deleteServerCredentialUseCase, "deleteServerCredentialUseCase 不能為空");
        this.reactivateServerCredentialUseCase = reactivateServerCredentialUseCase;
        this.resourceAssembler = Objects.requireNonNull(resourceAssembler, "resourceAssembler 不能為空");
    }

    /**
     * 向下相容建構子：提供 6 參數建構支援
     */
    public ServerCredentialController(CreateServerCredentialUseCase createServerCredentialUseCase,
                                     UpdateServerCredentialUseCase updateServerCredentialUseCase,
                                     GetServerCredentialUseCase getServerCredentialUseCase,
                                     ListServerCredentialsUseCase listServerCredentialsUseCase,
                                     DeleteServerCredentialUseCase deleteServerCredentialUseCase,
                                     ServerCredentialResourceAssembler resourceAssembler) {
        this(createServerCredentialUseCase, updateServerCredentialUseCase, getServerCredentialUseCase,
             listServerCredentialsUseCase, deleteServerCredentialUseCase, null, resourceAssembler);
    }

    @Operation(summary = "建立全新伺服器連線憑證", description = "註冊主機連線帳號密碼或 SSH 私鑰，後端自動以 AES-GCM 密文落盤保存")
    @PostMapping
    public ResponseEntity<ServerCredentialRetrievedResource> createCredential(
            @Valid @RequestBody CreateServerCredentialResource resource) {
        CreateServerCredentialCommand command = resourceAssembler.toCreateCommand(resource);
        ServerCredentialGottenResult result = createServerCredentialUseCase.createCredential(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(resourceAssembler.toRetrievedResource(result));
    }

    @Operation(summary = "更新既有伺服器連線憑證", description = "依憑證 ID 更新主機連線資訊、帳密或私鑰")
    @PutMapping("/{id}")
    public ResponseEntity<ServerCredentialRetrievedResource> updateCredential(
            @Parameter(description = "憑證唯一識別碼") @PathVariable @NotBlank(message = "憑證唯一識別碼不能為空") String id,
            @Valid @RequestBody UpdateServerCredentialResource resource) {
        UpdateServerCredentialCommand command = resourceAssembler.toUpdateCommand(id, resource);
        ServerCredentialGottenResult result = updateServerCredentialUseCase.updateCredential(command);
        return ResponseEntity.ok(resourceAssembler.toRetrievedResource(result));
    }

    @Operation(summary = "依 ID 查詢憑證詳細資料", description = "取得憑證資訊 (密碼與私鑰已進行脫敏遮罩)")
    @GetMapping("/{id}")
    public ResponseEntity<ServerCredentialRetrievedResource> getCredentialById(
            @Parameter(description = "憑證唯一識別碼") @PathVariable @NotBlank(message = "憑證唯一識別碼不能為空") String id) {
        GetServerCredentialQuery query = GetServerCredentialQuery.byId(id);
        ServerCredentialGottenResult result = getServerCredentialUseCase.getCredential(query);
        return ResponseEntity.ok(resourceAssembler.toRetrievedResource(result));
    }

    @Operation(summary = "依別名 (Alias) 查詢憑證詳細資料", description = "以易讀別名快速定位伺服器連線憑證")
    @GetMapping("/alias/{alias}")
    public ResponseEntity<ServerCredentialRetrievedResource> getCredentialByAlias(
            @Parameter(description = "憑證別名") @PathVariable @NotBlank(message = "憑證別名不能為空") String alias) {
        GetServerCredentialQuery query = GetServerCredentialQuery.byAlias(alias);
        ServerCredentialGottenResult result = getServerCredentialUseCase.getCredential(query);
        return ResponseEntity.ok(resourceAssembler.toRetrievedResource(result));
    }

    @Operation(summary = "查詢所有憑證或依條件搜尋", description = "列出已註冊憑證；可傳入 keyword 模糊搜尋或以 active (Y/N) 過濾啟用/軟刪除狀態")
    @GetMapping
    public ResponseEntity<List<ServerCredentialRetrievedResource>> listCredentials(
            @Parameter(description = "搜尋關鍵字 (選填)") @RequestParam(required = false) String keyword,
            @Parameter(description = "啟用狀態過濾 (選填，Y=僅查詢啟用, N=僅查詢停用/軟刪除, 未提供=全量查詢)") @RequestParam(required = false) String active) {
        ListServerCredentialsQuery query = resourceAssembler.toListQuery(keyword, active);
        List<ServerCredentialGottenResult> results = listServerCredentialsUseCase.listCredentials(query);
        return ResponseEntity.ok(resourceAssembler.toRetrievedResourceList(results));
    }

    @Operation(summary = "依 ID 軟刪除伺服器憑證", description = "將指定之伺服器憑證標記為停用/軟刪除狀態 (保留資料庫落盤歷史紀錄但禁止用於傳輸作業)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCredential(
            @Parameter(description = "憑證唯一識別碼或別名") @PathVariable @NotBlank(message = "憑證 ID 不能為空") String id) {
        DeleteServerCredentialCommand command = new DeleteServerCredentialCommand(id);
        deleteServerCredentialUseCase.deleteCredential(command);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "依 ID 重新啟用/生效伺服器憑證", description = "將停用或軟刪除之伺服器憑證重新啟用生效 (恢復其可用於傳輸作業之有效狀態)")
    @RequestMapping(value = "/{id}/reactivate", method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<ServerCredentialRetrievedResource> reactivateCredential(
            @Parameter(description = "憑證唯一識別碼或別名") @PathVariable @NotBlank(message = "憑證 ID 不能為空") String id) {
        if (reactivateServerCredentialUseCase == null) {
            throw new IllegalStateException("ReactivateServerCredentialUseCase 未配置");
        }
        ReactivateServerCredentialCommand command = new ReactivateServerCredentialCommand(id);
        ServerCredentialGottenResult result = reactivateServerCredentialUseCase.reactivateCredential(command);
        return ResponseEntity.ok(resourceAssembler.toRetrievedResource(result));
    }
}
