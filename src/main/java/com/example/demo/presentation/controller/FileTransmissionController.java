package com.example.demo.presentation.controller;

import com.example.demo.application.port.in.DownloadFileUseCase;
import com.example.demo.application.port.in.GetTransmissionStatusUseCase;
import com.example.demo.application.port.in.SubmitDownloadFileUseCase;
import com.example.demo.application.port.in.SubmitSyncDirectoryUseCase;
import com.example.demo.application.port.in.SubmitUploadFileUseCase;
import com.example.demo.application.port.in.SyncDirectoryUseCase;
import com.example.demo.application.port.in.UploadFileFromMemoryUseCase;
import com.example.demo.application.port.in.UploadFileUseCase;
import com.example.demo.application.port.out.ServerCredentialStoragePort;
import com.example.demo.application.shared.command.DownloadFileCommand;
import com.example.demo.application.shared.command.SyncDirectoryCommand;
import com.example.demo.application.shared.command.UploadFileCommand;
import com.example.demo.application.shared.command.UploadFileFromMemoryCommand;
import com.example.demo.application.shared.dto.DirectorySyncGottenResult;
import com.example.demo.application.shared.dto.FileTransmissionGottenResult;
import com.example.demo.application.shared.dto.FileTransmissionSubmittedResult;
import com.example.demo.application.shared.exception.FileTransmissionException;
import com.example.demo.application.shared.query.GetTransmissionStatusQuery;
import com.example.demo.presentation.assembler.TransmissionResourceAssembler;
import com.example.demo.presentation.resource.in.DownloadFileResource;
import com.example.demo.presentation.resource.in.SyncDirectoryResource;
import com.example.demo.presentation.resource.in.UploadFileMultipartResource;
import com.example.demo.presentation.resource.in.UploadFileResource;
import com.example.demo.presentation.resource.out.DirectorySynchronizedResource;
import com.example.demo.presentation.resource.out.FileDownloadedResource;
import com.example.demo.presentation.resource.out.FileTransmissionSubmittedResource;
import com.example.demo.presentation.resource.out.FileUploadedResource;
import com.example.demo.presentation.resource.out.TransmissionErrorOccurredResource;
import com.example.demo.presentation.resource.out.TransmissionStatusGottenResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/**
 * 檔案傳輸表現層控制器 (FileTransmissionController - Inbound Adapter)
 * <p>
 * 表現層 REST API 控制器。
 * 遵循六角形架構：
 * 1. 接收 Request Resource (純資料載體)，透過 Spring Valid (@Valid / @Validated) 進行前端輸入檢核防禦；
 * 2. 透過 TransmissionResourceAssembler 防腐層轉換為 Application Layer Command；
 * 3. 呼叫對應的 Inbound Port (UseCase)；
 * 4. 將回傳之 Result DTO 透過 Assembler 轉換為 Response Resource 回傳前端；
 * 5. 支援同步與非同步長任務（HTTP 202 Accepted 立即回傳 trackingId，並提供 /status/{trackingId} 查詢端點）。
 * </p>
 */
@Tag(name = "檔案傳輸 API (File Transmission API)", description = "提供同步/非同步檔案上傳、檔案下載、目錄同步及任務狀態查詢端點")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Validated
@RestController
@RequestMapping("/api/transmission")
public class FileTransmissionController {

    private final UploadFileUseCase uploadFileUseCase;
    private final DownloadFileUseCase downloadFileUseCase;
    private final SyncDirectoryUseCase syncDirectoryUseCase;
    private final SubmitUploadFileUseCase submitUploadFileUseCase;
    private final SubmitDownloadFileUseCase submitDownloadFileUseCase;
    private final SubmitSyncDirectoryUseCase submitSyncDirectoryUseCase;
    private final GetTransmissionStatusUseCase getTransmissionStatusUseCase;
    private final UploadFileFromMemoryUseCase uploadFileFromMemoryUseCase;
    private final TransmissionResourceAssembler resourceAssembler;

    /**
     * 主要建構子 (供 Spring 容器依賴注入)
     *
     * @param uploadFileUseCase            同步上傳 UseCase
     * @param downloadFileUseCase          同步下載 UseCase
     * @param syncDirectoryUseCase         同步目錄 UseCase
     * @param submitUploadFileUseCase      非同步上傳 UseCase
     * @param submitDownloadFileUseCase    非同步下載 UseCase
     * @param submitSyncDirectoryUseCase   非同步目錄同步 UseCase
     * @param getTransmissionStatusUseCase 查詢傳輸狀態 UseCase
     * @param uploadFileFromMemoryUseCase  記憶體直傳 UseCase
     * @param resourceAssembler            表現層資源組裝器 (Anti-Corruption Layer)
     */
    @Autowired
    public FileTransmissionController(
            UploadFileUseCase uploadFileUseCase,
            DownloadFileUseCase downloadFileUseCase,
            SyncDirectoryUseCase syncDirectoryUseCase,
            SubmitUploadFileUseCase submitUploadFileUseCase,
            SubmitDownloadFileUseCase submitDownloadFileUseCase,
            SubmitSyncDirectoryUseCase submitSyncDirectoryUseCase,
            GetTransmissionStatusUseCase getTransmissionStatusUseCase,
            UploadFileFromMemoryUseCase uploadFileFromMemoryUseCase,
            @Autowired(required = false) TransmissionResourceAssembler resourceAssembler
    ) {
        this.uploadFileUseCase = uploadFileUseCase;
        this.downloadFileUseCase = downloadFileUseCase;
        this.syncDirectoryUseCase = syncDirectoryUseCase;
        this.submitUploadFileUseCase = submitUploadFileUseCase;
        this.submitDownloadFileUseCase = submitDownloadFileUseCase;
        this.submitSyncDirectoryUseCase = submitSyncDirectoryUseCase;
        this.getTransmissionStatusUseCase = getTransmissionStatusUseCase;
        this.uploadFileFromMemoryUseCase = uploadFileFromMemoryUseCase;
        this.resourceAssembler = resourceAssembler != null ? resourceAssembler : new TransmissionResourceAssembler();
    }

    /**
     * 向下相容建構子 (7 參數版本)：維持既有單元測試完全相容
     */
    public FileTransmissionController(
            UploadFileUseCase uploadFileUseCase,
            DownloadFileUseCase downloadFileUseCase,
            SyncDirectoryUseCase syncDirectoryUseCase,
            SubmitUploadFileUseCase submitUploadFileUseCase,
            SubmitDownloadFileUseCase submitDownloadFileUseCase,
            SubmitSyncDirectoryUseCase submitSyncDirectoryUseCase,
            GetTransmissionStatusUseCase getTransmissionStatusUseCase
    ) {
        this(uploadFileUseCase, downloadFileUseCase, syncDirectoryUseCase, submitUploadFileUseCase, submitDownloadFileUseCase, submitSyncDirectoryUseCase, getTransmissionStatusUseCase, null, new TransmissionResourceAssembler());
    }

    /**
     * 向下相容建構子 (8 參數版本)
     */
    public FileTransmissionController(
            UploadFileUseCase uploadFileUseCase,
            DownloadFileUseCase downloadFileUseCase,
            SyncDirectoryUseCase syncDirectoryUseCase,
            SubmitUploadFileUseCase submitUploadFileUseCase,
            SubmitDownloadFileUseCase submitDownloadFileUseCase,
            SubmitSyncDirectoryUseCase submitSyncDirectoryUseCase,
            GetTransmissionStatusUseCase getTransmissionStatusUseCase,
            UploadFileFromMemoryUseCase uploadFileFromMemoryUseCase
    ) {
        this(uploadFileUseCase, downloadFileUseCase, syncDirectoryUseCase, submitUploadFileUseCase, submitDownloadFileUseCase, submitSyncDirectoryUseCase, getTransmissionStatusUseCase, uploadFileFromMemoryUseCase, new TransmissionResourceAssembler());
    }

    /**
     * 向下相容建構子 (10 參數版本)：維持既有單元測試相容
     */
    public FileTransmissionController(
            UploadFileUseCase uploadFileUseCase,
            DownloadFileUseCase downloadFileUseCase,
            SyncDirectoryUseCase syncDirectoryUseCase,
            SubmitUploadFileUseCase submitUploadFileUseCase,
            SubmitDownloadFileUseCase submitDownloadFileUseCase,
            SubmitSyncDirectoryUseCase submitSyncDirectoryUseCase,
            GetTransmissionStatusUseCase getTransmissionStatusUseCase,
            UploadFileFromMemoryUseCase uploadFileFromMemoryUseCase,
            ObjectMapper objectMapper,
            ServerCredentialStoragePort serverCredentialStoragePort
    ) {
        this(uploadFileUseCase, downloadFileUseCase, syncDirectoryUseCase, submitUploadFileUseCase, submitDownloadFileUseCase, submitSyncDirectoryUseCase, getTransmissionStatusUseCase, uploadFileFromMemoryUseCase, new TransmissionResourceAssembler(serverCredentialStoragePort, objectMapper, null));
    }

    /**
     * 向下相容建構子 (11 參數版本)
     */
    public FileTransmissionController(
            UploadFileUseCase uploadFileUseCase,
            DownloadFileUseCase downloadFileUseCase,
            SyncDirectoryUseCase syncDirectoryUseCase,
            SubmitUploadFileUseCase submitUploadFileUseCase,
            SubmitDownloadFileUseCase submitDownloadFileUseCase,
            SubmitSyncDirectoryUseCase submitSyncDirectoryUseCase,
            GetTransmissionStatusUseCase getTransmissionStatusUseCase,
            UploadFileFromMemoryUseCase uploadFileFromMemoryUseCase,
            ObjectMapper objectMapper,
            ServerCredentialStoragePort serverCredentialStoragePort,
            Validator validator
    ) {
        this(uploadFileUseCase, downloadFileUseCase, syncDirectoryUseCase, submitUploadFileUseCase, submitDownloadFileUseCase, submitSyncDirectoryUseCase, getTransmissionStatusUseCase, uploadFileFromMemoryUseCase, new TransmissionResourceAssembler(serverCredentialStoragePort, objectMapper, validator));
    }

    /**
     * 同步上傳檔案 API
     *
     * @param resource 上傳請求資料載體
     * @return 上傳完成回應資料載體
     */
    @Operation(summary = "同步上傳檔案", description = "依據指定的協定 (SFTP / FTP / FTPS) 將本機檔案同步上傳至遠端目標路徑，並回傳上傳結果。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "上傳成功",
                    content = @Content(schema = @Schema(implementation = FileUploadedResource.class))),
            @ApiResponse(responseCode = "400", description = "請求參數錯誤或檔案路徑無效",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class))),
            @ApiResponse(responseCode = "500", description = "檔案傳輸作業執行失敗",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class))),
            @ApiResponse(responseCode = "502", description = "遠端伺服器連線或驗證失敗",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class)))
    })
    @PostMapping("/upload")
    public ResponseEntity<FileUploadedResource> upload(@Valid @RequestBody UploadFileResource resource) {
        UploadFileCommand command = resourceAssembler.toCommand(resource);
        FileTransmissionGottenResult result = uploadFileUseCase.uploadFile(command);
        FileUploadedResource response = resourceAssembler.toUploadedResource(result);
        return ResponseEntity.ok(response);
    }

    /**
     * 非同步提交上傳檔案長任務 API (Mode 1)
     *
     * @param resource 上傳請求資料載體
     * @return 202 Accepted 包含 trackingId 與進行中狀態
     */
    @Operation(summary = "非同步提交檔案上傳任務", description = "將檔案上傳長任務提交至背景執行緒池執行，立即回傳 HTTP 202 Accepted 與 trackingId，後續可透過 /status/{trackingId} 查詢進度。")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "已成功接收並提交至背景執行",
                    content = @Content(schema = @Schema(implementation = FileTransmissionSubmittedResource.class))),
            @ApiResponse(responseCode = "400", description = "請求參數錯誤",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class))),
            @ApiResponse(responseCode = "500", description = "伺服器內部異常",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class)))
    })
    @PostMapping("/upload/async")
    public ResponseEntity<FileTransmissionSubmittedResource> submitUpload(@Valid @RequestBody UploadFileResource resource) {
        UploadFileCommand command = resourceAssembler.toCommand(resource);
        FileTransmissionSubmittedResult result = submitUploadFileUseCase.submitUploadFile(command);
        FileTransmissionSubmittedResource response = resourceAssembler.toSubmittedResource(result);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Multipart 檔案上傳 API (Zero Disk I/O 記憶體直傳)
     *
     * @param file     上傳檔案二進位資料串流 (MultipartFile)
     * @param resource 伺服器配置與目標目錄資訊 (支援 Swagger UI JSON 部件或 Form 表單欄位)
     * @param request  HTTP 原生請求物件
     * @return 上傳完成回應資料載體
     */
    @Operation(summary = "Multipart 表單檔案上傳 (記憶體直傳)", description = "透過 HTTP Multipart 直接上傳檔案串流，後端零磁碟落盤，直接依指定協定 (SFTP / FTP / FTPS) 傳輸至遠端目的地。支援直接填寫 JSON 配置 (Swagger UI) 或個別表單欄位。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "上傳成功",
                    content = @Content(schema = @Schema(implementation = FileUploadedResource.class))),
            @ApiResponse(responseCode = "400", description = "請求參數錯誤或檔案為空",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class))),
            @ApiResponse(responseCode = "500", description = "檔案傳輸作業執行失敗",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class))),
            @ApiResponse(responseCode = "502", description = "遠端伺服器連線或驗證失敗",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class)))
    })
    @PostMapping(value = "/upload/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadedResource> uploadFile(
            @Parameter(description = "待上傳的檔案 (二進位串流)", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "伺服器配置與目標目錄資訊 (可直接編輯 JSON 或透過表單欄位傳入)", required = false)
            @ModelAttribute UploadFileMultipartResource resource,
            @Parameter(hidden = true)
            HttpServletRequest request
    ) {
        // 1. 委派防腐組裝器相容解析有效 Resource (支援 Swagger JSON 部件、Servlet Part 與 Form-data)
        UploadFileMultipartResource effectiveResource = resourceAssembler.resolveEffectiveResource(resource, request);

        // 2. 委派組裝器將 Resource 與上傳檔案封裝為記憶體直傳 Command
        UploadFileFromMemoryCommand command = resourceAssembler.toCommand(effectiveResource, file);

        // 3. 呼叫 UseCase 業務流程並轉換為 Response 資源
        FileTransmissionGottenResult result = uploadFileFromMemoryUseCase.uploadFileFromMemory(command);
        FileUploadedResource response = resourceAssembler.toUploadedResource(result);
        return ResponseEntity.ok(response);
    }

    /**
     * 同步下載檔案 API
     *
     * @param resource 下載請求資料載體
     * @return 下載完成回應資料載體
     */
    @Operation(summary = "同步下載檔案", description = "依據指定的協定 (SFTP / FTP / FTPS) 將遠端檔案同步下載至本機儲存路徑，並回傳下載結果。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "下載成功",
                    content = @Content(schema = @Schema(implementation = FileDownloadedResource.class))),
            @ApiResponse(responseCode = "400", description = "請求參數錯誤或路徑非法",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class))),
            @ApiResponse(responseCode = "500", description = "檔案下載作業執行失敗",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class))),
            @ApiResponse(responseCode = "502", description = "遠端伺服器連線或驗證失敗",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class)))
    })
    @PostMapping("/download")
    public ResponseEntity<FileDownloadedResource> download(@Valid @RequestBody DownloadFileResource resource) {
        DownloadFileCommand command = resourceAssembler.toCommand(resource);
        FileTransmissionGottenResult result = downloadFileUseCase.downloadFile(command);
        FileDownloadedResource response = resourceAssembler.toDownloadedResource(result);
        return ResponseEntity.ok(response);
    }

    /**
     * 非同步提交下載檔案長任務 API (Mode 1)
     *
     * @param resource 下載請求資料載體
     * @return 202 Accepted 包含 trackingId 與進行中狀態
     */
    @Operation(summary = "非同步提交檔案下載任務", description = "將檔案下載長任務提交至背景執行緒池執行，立即回傳 HTTP 202 Accepted 與 trackingId，後續可透過 /status/{trackingId} 查詢進度。")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "已成功接收並提交至背景執行",
                    content = @Content(schema = @Schema(implementation = FileTransmissionSubmittedResource.class))),
            @ApiResponse(responseCode = "400", description = "請求參數錯誤",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class))),
            @ApiResponse(responseCode = "500", description = "伺服器內部異常",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class)))
    })
    @PostMapping("/download/async")
    public ResponseEntity<FileTransmissionSubmittedResource> submitDownload(@Valid @RequestBody DownloadFileResource resource) {
        DownloadFileCommand command = resourceAssembler.toCommand(resource);
        FileTransmissionSubmittedResult result = submitDownloadFileUseCase.submitDownloadFile(command);
        FileTransmissionSubmittedResource response = resourceAssembler.toSubmittedResource(result);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * 同步目錄同步 API
     *
     * @param resource 同步請求資料載體
     * @return 同步完成回應資料載體
     */
    @Operation(summary = "同步目錄雙向同步", description = "執行本機目錄與遠端目錄的單向或雙向比對傳輸同步 (LOCAL_TO_REMOTE 或 REMOTE_TO_LOCAL)，支援可選刪除遠端多餘檔案。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "目錄同步成功",
                    content = @Content(schema = @Schema(implementation = DirectorySynchronizedResource.class))),
            @ApiResponse(responseCode = "400", description = "請求參數錯誤或目錄路徑不合法",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class))),
            @ApiResponse(responseCode = "500", description = "目錄同步作業失敗",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class))),
            @ApiResponse(responseCode = "502", description = "遠端伺服器連線或驗證失敗",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class)))
    })
    @PostMapping("/sync")
    public ResponseEntity<DirectorySynchronizedResource> sync(@Valid @RequestBody SyncDirectoryResource resource) {
        SyncDirectoryCommand command = resourceAssembler.toCommand(resource);
        DirectorySyncGottenResult result = syncDirectoryUseCase.syncDirectory(command);
        DirectorySynchronizedResource response = resourceAssembler.toSynchronizedResource(result);
        return ResponseEntity.ok(response);
    }

    /**
     * 非同步提交目錄同步長任務 API (Mode 1)
     *
     * @param resource 同步請求資料載體
     * @return 202 Accepted 包含 trackingId 與進行中狀態
     */
    @Operation(summary = "非同步提交目錄同步長任務", description = "將目錄同步長任務提交至背景執行緒池執行，立即回傳 HTTP 202 Accepted 與 trackingId，後續可透過 /status/{trackingId} 查詢進度。")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "已成功接收並提交至背景執行",
                    content = @Content(schema = @Schema(implementation = FileTransmissionSubmittedResource.class))),
            @ApiResponse(responseCode = "400", description = "請求參數錯誤",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class))),
            @ApiResponse(responseCode = "500", description = "伺服器內部異常",
                    content = @Content(schema = @Schema(implementation = TransmissionErrorOccurredResource.class)))
    })
    @PostMapping("/sync/async")
    public ResponseEntity<FileTransmissionSubmittedResource> submitSync(@Valid @RequestBody SyncDirectoryResource resource) {
        SyncDirectoryCommand command = resourceAssembler.toCommand(resource);
        FileTransmissionSubmittedResult result = submitSyncDirectoryUseCase.submitSyncDirectory(command);
        FileTransmissionSubmittedResource response = resourceAssembler.toSubmittedResource(result);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * 查詢傳輸作業狀態與結果 API (Mode 1)
     *
     * @param trackingId 傳輸作業追蹤唯一識別碼
     * @return 200 OK 狀態回應資料載體，若查無紀錄則回傳 404 Not Found
     */
    @Operation(summary = "查詢傳輸作業狀態與結果", description = "依據非同步提交獲得的 trackingId 查詢目前傳輸作業的執行狀態 (IN_PROGRESS, COMPLETED, FAILED) 與詳細審計指標。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查詢成功",
                    content = @Content(schema = @Schema(implementation = TransmissionStatusGottenResource.class))),
            @ApiResponse(responseCode = "404", description = "查無該傳輸追蹤代碼紀錄")
    })
    @GetMapping("/status/{trackingId}")
    public ResponseEntity<TransmissionStatusGottenResource> getStatus(
            @Parameter(name = "trackingId", description = "傳輸作業追蹤唯一識別碼", required = true, example = "tx-20261017-001")
            @PathVariable("trackingId") @NotBlank(message = "傳輸作業追蹤唯一識別碼 (trackingId) 不能為空") String trackingId
    ) {
        GetTransmissionStatusQuery query = GetTransmissionStatusQuery.of(trackingId);
        Optional<FileTransmissionGottenResult> statusOpt = getTransmissionStatusUseCase.getTransmissionStatus(query);

        return statusOpt
                .map(resourceAssembler::toStatusResource)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
