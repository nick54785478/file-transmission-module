package com.example.demo.presentation.controller;

import com.example.demo.application.port.in.DownloadFileUseCase;
import com.example.demo.application.port.in.GetTransmissionStatusUseCase;
import com.example.demo.application.port.in.SubmitDownloadFileUseCase;
import com.example.demo.application.port.in.SubmitSyncDirectoryUseCase;
import com.example.demo.application.port.in.SubmitUploadFileUseCase;
import com.example.demo.application.port.in.SyncDirectoryUseCase;
import com.example.demo.application.port.in.UploadFileFromMemoryUseCase;
import com.example.demo.application.port.in.UploadFileUseCase;
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
import com.example.demo.application.port.out.ServerCredentialStoragePort;
import com.example.demo.presentation.resource.out.TransmissionStatusGottenResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.io.InputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.web.multipart.MultipartHttpServletRequest;

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
    private final ObjectMapper objectMapper;
    private final ServerCredentialStoragePort serverCredentialStoragePort;
    private final Validator validator;

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
        this(uploadFileUseCase, downloadFileUseCase, syncDirectoryUseCase, submitUploadFileUseCase, submitDownloadFileUseCase, submitSyncDirectoryUseCase, getTransmissionStatusUseCase, null, new ObjectMapper(), null, null);
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
        this(uploadFileUseCase, downloadFileUseCase, syncDirectoryUseCase, submitUploadFileUseCase, submitDownloadFileUseCase, submitSyncDirectoryUseCase, getTransmissionStatusUseCase, uploadFileFromMemoryUseCase, new ObjectMapper(), null, null);
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
        this(uploadFileUseCase, downloadFileUseCase, syncDirectoryUseCase, submitUploadFileUseCase, submitDownloadFileUseCase, submitSyncDirectoryUseCase, getTransmissionStatusUseCase, uploadFileFromMemoryUseCase, objectMapper, serverCredentialStoragePort, null);
    }

    /**
     * 完整建構子 (供 Spring 容器依賴注入)
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
            @Autowired(required = false) ObjectMapper objectMapper,
            @Autowired(required = false) ServerCredentialStoragePort serverCredentialStoragePort,
            @Autowired(required = false) Validator validator
    ) {
        this.uploadFileUseCase = uploadFileUseCase;
        this.downloadFileUseCase = downloadFileUseCase;
        this.syncDirectoryUseCase = syncDirectoryUseCase;
        this.submitUploadFileUseCase = submitUploadFileUseCase;
        this.submitDownloadFileUseCase = submitDownloadFileUseCase;
        this.submitSyncDirectoryUseCase = submitSyncDirectoryUseCase;
        this.getTransmissionStatusUseCase = getTransmissionStatusUseCase;
        this.uploadFileFromMemoryUseCase = uploadFileFromMemoryUseCase;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.serverCredentialStoragePort = serverCredentialStoragePort;
        this.validator = validator;
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
        UploadFileCommand command = TransmissionResourceAssembler.toCommand(resource, serverCredentialStoragePort);
        FileTransmissionGottenResult result = uploadFileUseCase.uploadFile(command);
        FileUploadedResource response = TransmissionResourceAssembler.toUploadedResource(result);
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
        UploadFileCommand command = TransmissionResourceAssembler.toCommand(resource, serverCredentialStoragePort);
        FileTransmissionSubmittedResult result = submitUploadFileUseCase.submitUploadFile(command);
        FileTransmissionSubmittedResource response = TransmissionResourceAssembler.toSubmittedResource(result);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Multipart 檔案上傳 API (Zero Disk I/O 記憶體直傳)
     *
     * @param file 上傳檔案二進位資料串流 (MultipartFile)
     * @param resource 伺服器配置與目標目錄資訊 (支援 Swagger UI JSON 部件或 Form 表單欄位)
     * @param request HTTP 原生請求物件
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
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上傳檔案不能為空");
        }
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new FileTransmissionException("讀取上傳檔案串流失敗: " + e.getMessage(), e);
        }

        // 智慧相容解析 resource：
        // 1. 嘗試從 Multipart request 中的 "resource" 部件解析 JSON (Swagger UI 送出之 application/octet-stream 或 application/json)
        UploadFileMultipartResource effectiveResource = null;
        if (request instanceof MultipartHttpServletRequest multipartRequest) {
            MultipartFile resourcePart = multipartRequest.getFile("resource");
            if (resourcePart != null && !resourcePart.isEmpty()) {
                try {
                    effectiveResource = objectMapper.readValue(resourcePart.getBytes(), UploadFileMultipartResource.class);
                } catch (Exception ignored) {
                }
            }
        }

        // 2. 備援嘗試從 Servlet 3.0 getPart("resource") 讀取串流
        if (effectiveResource == null || effectiveResource.credentialId() == null) {
            try {
                Part part = request != null ? request.getPart("resource") : null;
                if (part != null && part.getSize() > 0) {
                    try (InputStream is = part.getInputStream()) {
                        effectiveResource = objectMapper.readValue(is, UploadFileMultipartResource.class);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 3. 嘗試從 request parameter "resource" (字串參數) 解析 JSON
        if (effectiveResource == null || effectiveResource.credentialId() == null) {
            String rawJson = request != null ? request.getParameter("resource") : null;
            if (rawJson != null && !rawJson.trim().isEmpty()) {
                try {
                    effectiveResource = objectMapper.readValue(rawJson, UploadFileMultipartResource.class);
                } catch (Exception ignored) {
                }
            }
        }

        // 4. 若以上皆無提供 JSON 部件，回退使用 @ModelAttribute 繫結的表單普通欄位 (如 cURL -F "credentialId=..." 或 HTML 表單)
        if (effectiveResource == null || effectiveResource.credentialId() == null) {
            if (resource != null && resource.credentialId() != null) {
                effectiveResource = resource;
            }
        }

        if (effectiveResource == null) {
            effectiveResource = resource;
        }

        // 基本欄位校驗 (Spring Validation / JSR-380)
        if (validator != null && effectiveResource != null) {
            Set<ConstraintViolation<UploadFileMultipartResource>> violations = validator.validate(effectiveResource);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
        } else if (effectiveResource != null) {
            if (effectiveResource.credentialId() == null || effectiveResource.credentialId().isBlank()) {
                throw new IllegalArgumentException("後端伺服器憑證 ID (credentialId) 不能為空");
            }
            if (effectiveResource.remotePath() == null || effectiveResource.remotePath().isBlank()) {
                throw new IllegalArgumentException("遠端目標工作目錄 (remotePath) 不能為空");
            }
        }

        UploadFileFromMemoryCommand command = TransmissionResourceAssembler.toCommand(
                effectiveResource,
                fileBytes,
                file.getOriginalFilename(),
                serverCredentialStoragePort
        );
        FileTransmissionGottenResult result = uploadFileFromMemoryUseCase.uploadFileFromMemory(command);
        FileUploadedResource response = TransmissionResourceAssembler.toUploadedResource(result);
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
        DownloadFileCommand command = TransmissionResourceAssembler.toCommand(resource, serverCredentialStoragePort);
        FileTransmissionGottenResult result = downloadFileUseCase.downloadFile(command);
        FileDownloadedResource response = TransmissionResourceAssembler.toDownloadedResource(result);
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
        DownloadFileCommand command = TransmissionResourceAssembler.toCommand(resource, serverCredentialStoragePort);
        FileTransmissionSubmittedResult result = submitDownloadFileUseCase.submitDownloadFile(command);
        FileTransmissionSubmittedResource response = TransmissionResourceAssembler.toSubmittedResource(result);
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
        SyncDirectoryCommand command = TransmissionResourceAssembler.toCommand(resource, serverCredentialStoragePort);
        DirectorySyncGottenResult result = syncDirectoryUseCase.syncDirectory(command);
        DirectorySynchronizedResource response = TransmissionResourceAssembler.toSynchronizedResource(result);
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
        SyncDirectoryCommand command = TransmissionResourceAssembler.toCommand(resource, serverCredentialStoragePort);
        FileTransmissionSubmittedResult result = submitSyncDirectoryUseCase.submitSyncDirectory(command);
        FileTransmissionSubmittedResource response = TransmissionResourceAssembler.toSubmittedResource(result);
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
                .map(TransmissionResourceAssembler::toStatusResource)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
