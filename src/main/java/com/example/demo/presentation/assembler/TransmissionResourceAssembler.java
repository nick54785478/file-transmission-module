package com.example.demo.presentation.assembler;

import com.example.demo.application.shared.command.DownloadFileCommand;
import com.example.demo.application.shared.command.SyncDirectoryCommand;
import com.example.demo.application.shared.command.UploadFileCommand;
import com.example.demo.application.shared.command.UploadFileFromMemoryCommand;
import com.example.demo.application.shared.dto.DirectorySyncGottenResult;
import com.example.demo.application.shared.dto.FileTransmissionGottenResult;
import com.example.demo.application.shared.dto.FileTransmissionSubmittedResult;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
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
import com.example.demo.application.port.out.ServerCredentialStoragePort;
import com.example.demo.application.shared.exception.FileTransmissionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 表現層資料組裝器 (TransmissionResourceAssembler - Anti-Corruption Layer / Presentation Component)
 * <p>
 * <b>架構與設計職責 (AGENTS.md 第 2 節防腐層與組裝器規範)：</b>
 * <ul>
 *   <li>標註為 Spring <code>@Component</code>，遵循六角形架構表現層防腐層原則，隔離 HTTP 傳入的 Resource 與 Application 層 Command / Result。</li>
 *   <li>負責將 Request Resource 轉換為 UseCase Command，並將 UseCase 回傳之 Result DTO 轉換為 Response Resource。</li>
 *   <li>依賴注入 {@link ServerCredentialStoragePort}、{@link ObjectMapper} 與 {@link Validator}，封裝憑證查詢、Multipart JSON 剖析與參數校驗。</li>
 *   <li>保留公開靜態輔助方法，提供例外處理器 (如 {@link com.example.demo.presentation.handler.GlobalExceptionHandler}) 與單元測試向下相容支援。</li>
 * </ul>
 * </p>
 */
@Component
public class TransmissionResourceAssembler {

    private final ServerCredentialStoragePort serverCredentialStoragePort;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * 無參建構子 (供測試或無 Spring 環境手動實例化)
     */
    public TransmissionResourceAssembler() {
        this(null, new ObjectMapper(), null);
    }

    /**
     * 基礎建構子 (僅注入憑證儲存埠)
     *
     * @param serverCredentialStoragePort 憑證儲存埠
     */
    public TransmissionResourceAssembler(ServerCredentialStoragePort serverCredentialStoragePort) {
        this(serverCredentialStoragePort, new ObjectMapper(), null);
    }

    /**
     * 完整 Spring 依賴注入建構子
     *
     * @param serverCredentialStoragePort 憑證儲存埠
     * @param objectMapper                JSON 序列化與反序列化器
     * @param validator                   Bean Validation 檢核器
     */
    @Autowired
    public TransmissionResourceAssembler(
            @Autowired(required = false) ServerCredentialStoragePort serverCredentialStoragePort,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Autowired(required = false) Validator validator
    ) {
        this.serverCredentialStoragePort = serverCredentialStoragePort;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.validator = validator;
    }

    /**
     * 將 UploadFileResource 請求載體轉換為 UploadFileCommand 應用命令 (使用組件注入之憑證儲存埠)
     *
     * @param resource 上傳請求資源載體
     * @return 檔案上傳應用命令
     */
    public UploadFileCommand toCommand(UploadFileResource resource) {
        return toCommand(resource, this.serverCredentialStoragePort);
    }

    /**
     * 將 UploadFileResource 請求載體轉換為 UploadFileCommand 應用命令 (透過憑證儲存埠依 credentialId 自動解析配置)
     */
    public static UploadFileCommand toCommand(UploadFileResource resource, ServerCredentialStoragePort credentialStoragePort) {
        if (resource == null) {
            throw new IllegalArgumentException("UploadFileResource 不能為空");
        }
        TransmissionServerConfig serverConfig = resolveServerConfig(resource.credentialId(), credentialStoragePort);

        return new UploadFileCommand(
                serverConfig,
                LocalFilePath.of(resource.localFilePath()),
                RemoteFilePath.of(resource.remotePath()),
                resource.filename()
        );
    }

    /**
     * 將 UploadFileMultipartResource 與檔案二進位轉換為 UploadFileFromMemoryCommand (使用組件注入之憑證儲存埠)
     *
     * @param resource         上傳表單參數資源
     * @param fileContent      檔案內容二進位位元組陣列
     * @param originalFilename 原始檔名
     * @return 記憶體直傳應用命令
     */
    public UploadFileFromMemoryCommand toCommand(
            UploadFileMultipartResource resource,
            byte[] fileContent,
            String originalFilename
    ) {
        return toCommand(resource, fileContent, originalFilename, this.serverCredentialStoragePort);
    }

    /**
     * 將 UploadFileMultipartResource 與 MultipartFile 檔案串流轉換為 UploadFileFromMemoryCommand (使用組件注入之憑證儲存埠)
     *
     * @param resource 上傳表單參數資源
     * @param file     MultipartFile 上傳檔案
     * @return 記憶體直傳應用命令
     */
    public UploadFileFromMemoryCommand toCommand(
            UploadFileMultipartResource resource,
            MultipartFile file
    ) {
        return toCommand(resource, file, this.serverCredentialStoragePort);
    }

    /**
     * 將 UploadFileMultipartResource 與檔案二進位轉換為 UploadFileFromMemoryCommand (透過憑證儲存埠依 credentialId 自動解析配置)
     */
    public static UploadFileFromMemoryCommand toCommand(
            UploadFileMultipartResource resource,
            byte[] fileContent,
            String originalFilename,
            ServerCredentialStoragePort credentialStoragePort
    ) {
        if (resource == null) {
            throw new IllegalArgumentException("UploadFileMultipartResource 不能為空");
        }
        TransmissionServerConfig serverConfig = resolveServerConfig(resource.credentialId(), credentialStoragePort);

        String targetFilename = (resource.filename() != null && !resource.filename().trim().isEmpty())
                ? resource.filename().trim()
                : originalFilename;

        return new UploadFileFromMemoryCommand(
                serverConfig,
                fileContent,
                RemoteFilePath.of(resource.remotePath()),
                targetFilename
        );
    }

    /**
     * 將 UploadFileMultipartResource 與 MultipartFile 檔案串流轉換為 UploadFileFromMemoryCommand (透過憑證儲存埠依 credentialId 自動解析配置)
     *
     * @param resource               上傳表單參數資源
     * @param file                   MultipartFile 上傳檔案
     * @param credentialStoragePort  憑證儲存埠
     * @return 記憶體直傳應用命令
     */
    public static UploadFileFromMemoryCommand toCommand(
            UploadFileMultipartResource resource,
            MultipartFile file,
            ServerCredentialStoragePort credentialStoragePort
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
        return toCommand(resource, fileBytes, file.getOriginalFilename(), credentialStoragePort);
    }

    /**
     * 智慧相容解析 Multipart 上傳資源載體 (使用組件注入之 ObjectMapper 與 Validator)
     *
     * @param fallbackResource @ModelAttribute 繫結之表單資源
     * @param request          原生 HTTP 請求
     * @return 經解析與檢核通過之有效 UploadFileMultipartResource
     */
    public UploadFileMultipartResource resolveEffectiveResource(
            UploadFileMultipartResource fallbackResource,
            HttpServletRequest request
    ) {
        return resolveEffectiveResource(fallbackResource, request, this.objectMapper, this.validator);
    }

    /**
     * 智慧相容解析 Multipart 上傳資源載體 (支援 Swagger JSON 部件、Servlet Part、Form 表單或 ModelAttribute)
     *
     * @param fallbackResource @ModelAttribute 繫結之表單資源
     * @param request          原生 HTTP 請求
     * @param objectMapper     JSON 物件映射器 (若為空則使用預設)
     * @param validator        Bean Validation 檢核器 (選填)
     * @return 經解析與檢核通過之有效 UploadFileMultipartResource
     */
    public static UploadFileMultipartResource resolveEffectiveResource(
            UploadFileMultipartResource fallbackResource,
            HttpServletRequest request,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        UploadFileMultipartResource effectiveResource = null;
        ObjectMapper mapper = (objectMapper != null) ? objectMapper : new ObjectMapper();

        // 1. 嘗試從 Multipart request 中的 "resource" 部件解析 JSON (Swagger UI 送出之 application/octet-stream 或 application/json)
        if (request instanceof MultipartHttpServletRequest multipartRequest) {
            MultipartFile resourcePart = multipartRequest.getFile("resource");
            if (resourcePart != null && !resourcePart.isEmpty()) {
                try {
                    effectiveResource = mapper.readValue(resourcePart.getBytes(), UploadFileMultipartResource.class);
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
                        effectiveResource = mapper.readValue(is, UploadFileMultipartResource.class);
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
                    effectiveResource = mapper.readValue(rawJson, UploadFileMultipartResource.class);
                } catch (Exception ignored) {
                }
            }
        }

        // 4. 若以上皆無提供 JSON 部件，回退使用 @ModelAttribute 繫結的表單普通欄位 (如 cURL -F "credentialId=..." 或 HTML 表單)
        if (effectiveResource == null || effectiveResource.credentialId() == null) {
            if (fallbackResource != null && fallbackResource.credentialId() != null) {
                effectiveResource = fallbackResource;
            }
        }

        if (effectiveResource == null) {
            effectiveResource = fallbackResource;
        }

        // 基本欄位校驗 (Spring Validation / JSR-380)
        validateResource(effectiveResource, validator);

        return effectiveResource;
    }

    /**
     * 檢核 UploadFileMultipartResource 基本欄位有效性 (使用組件注入之 Validator)
     *
     * @param resource 待檢核資源
     */
    public void validateResource(UploadFileMultipartResource resource) {
        validateResource(resource, this.validator);
    }

    /**
     * 檢核 UploadFileMultipartResource 基本欄位有效性 (支援 JSR-380 與防禦性 fallback)
     *
     * @param resource  待檢核資源
     * @param validator Bean Validation 檢核器 (選填)
     */
    public static void validateResource(UploadFileMultipartResource resource, Validator validator) {
        if (resource == null) {
            throw new IllegalArgumentException("上傳參數 (resource) 不能為空");
        }
        if (validator != null) {
            Set<ConstraintViolation<UploadFileMultipartResource>> violations = validator.validate(resource);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
        } else {
            if (resource.credentialId() == null || resource.credentialId().isBlank()) {
                throw new IllegalArgumentException("後端伺服器憑證 ID (credentialId) 不能為空");
            }
            if (resource.remotePath() == null || resource.remotePath().isBlank()) {
                throw new IllegalArgumentException("遠端目標工作目錄 (remotePath) 不能為空");
            }
        }
    }

    /**
     * 將 DownloadFileResource 請求載體轉換為 DownloadFileCommand 應用命令 (使用組件注入之憑證儲存埠)
     *
     * @param resource 下載請求資源載體
     * @return 檔案下載應用命令
     */
    public DownloadFileCommand toCommand(DownloadFileResource resource) {
        return toCommand(resource, this.serverCredentialStoragePort);
    }

    /**
     * 將 DownloadFileResource 請求載體轉換為 DownloadFileCommand 應用命令 (透過憑證儲存埠依 credentialId 自動解析配置)
     */
    public static DownloadFileCommand toCommand(DownloadFileResource resource, ServerCredentialStoragePort credentialStoragePort) {
        if (resource == null) {
            throw new IllegalArgumentException("DownloadFileResource 不能為空");
        }
        TransmissionServerConfig serverConfig = resolveServerConfig(resource.credentialId(), credentialStoragePort);

        return new DownloadFileCommand(
                serverConfig,
                RemoteFilePath.of(resource.remoteFilePath()),
                LocalFilePath.of(resource.savedLocalPath())
        );
    }

    /**
     * 將 SyncDirectoryResource 請求載體轉換為 SyncDirectoryCommand 應用命令 (使用組件注入之憑證儲存埠)
     *
     * @param resource 目錄同步請求資源載體
     * @return 目錄同步應用命令
     */
    public SyncDirectoryCommand toCommand(SyncDirectoryResource resource) {
        return toCommand(resource, this.serverCredentialStoragePort);
    }

    /**
     * 將 SyncDirectoryResource 請求載體轉換為 SyncDirectoryCommand 應用命令 (透過憑證儲存埠依 credentialId 自動解析配置)
     */
    public static SyncDirectoryCommand toCommand(SyncDirectoryResource resource, ServerCredentialStoragePort credentialStoragePort) {
        if (resource == null) {
            throw new IllegalArgumentException("SyncDirectoryResource 不能為空");
        }
        TransmissionServerConfig serverConfig = resolveServerConfig(resource.credentialId(), credentialStoragePort);

        SyncDirection direction = SyncDirection.LOCAL_TO_REMOTE;
        if (resource.direction() != null && !resource.direction().trim().isEmpty()) {
            try {
                direction = SyncDirection.valueOf(resource.direction().trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        return new SyncDirectoryCommand(
                serverConfig,
                LocalFilePath.of(resource.localDir()),
                RemoteFilePath.of(resource.remoteDir()),
                direction,
                resource.deleteRemoteExtras() != null && resource.deleteRemoteExtras()
        );
    }

    private static TransmissionServerConfig resolveServerConfig(
            String credentialId,
            ServerCredentialStoragePort credentialStoragePort
    ) {
        if (credentialId == null || credentialId.trim().isEmpty()) {
            throw new IllegalArgumentException("未指定伺服器憑證 ID 或別名 (credentialId 為必填)");
        }
        if (credentialStoragePort == null) {
            throw new IllegalStateException("伺服器憑證儲存埠未配置 (ServerCredentialStoragePort is required)");
        }
        return credentialStoragePort.resolveTransmissionConfig(credentialId.trim())
                .orElseThrow(() -> new IllegalArgumentException("查無指定的伺服器憑證 [ID/別名: " + credentialId.trim() + "]"));
    }

    /**
     * 將 FileTransmissionGottenResult 轉換為 FileUploadedResource
     *
     * @param result DTO
     * @return FileUploadedResource
     */
    public FileUploadedResource toUploadedResource(FileTransmissionGottenResult result) {
        return new FileUploadedResource(
                result.transmissionId(),
                result.protocol(),
                result.status(),
                result.sourcePath(),
                result.targetPath(),
                result.fileName(),
                result.fileSizeBytes(),
                result.durationMillis(),
                result.errorMessage()
        );
    }

    /**
     * 將 FileTransmissionGottenResult 轉換為 FileDownloadedResource
     *
     * @param result DTO
     * @return FileDownloadedResource
     */
    public FileDownloadedResource toDownloadedResource(FileTransmissionGottenResult result) {
        return new FileDownloadedResource(
                result.transmissionId(),
                result.protocol(),
                result.status(),
                result.sourcePath(),
                result.targetPath(),
                result.fileName(),
                result.fileSizeBytes(),
                result.durationMillis(),
                result.errorMessage()
        );
    }

    /**
     * 將 DirectorySyncGottenResult 轉換為 DirectorySynchronizedResource
     *
     * @param result DTO
     * @return DirectorySynchronizedResource
     */
    public DirectorySynchronizedResource toSynchronizedResource(DirectorySyncGottenResult result) {
        return new DirectorySynchronizedResource(
                result.transmissionId(),
                result.protocol(),
                result.status(),
                result.direction(),
                result.localDir(),
                result.remoteDir(),
                result.filesTransferredCount(),
                result.filesDeletedCount(),
                result.durationMillis(),
                result.errorMessage()
        );
    }

    /**
     * 將 FileTransmissionSubmittedResult 轉換為 FileTransmissionSubmittedResource
     *
     * @param result DTO
     * @return FileTransmissionSubmittedResource
     */
    public FileTransmissionSubmittedResource toSubmittedResource(FileTransmissionSubmittedResult result) {
        return new FileTransmissionSubmittedResource(
                result.trackingId(),
                result.protocol(),
                result.status(),
                result.message(),
                result.submittedAt()
        );
    }

    /**
     * 將 FileTransmissionGottenResult 轉換為 TransmissionStatusGottenResource
     *
     * @param result DTO
     * @return TransmissionStatusGottenResource
     */
    public TransmissionStatusGottenResource toStatusResource(FileTransmissionGottenResult result) {
        return new TransmissionStatusGottenResource(
                result.transmissionId(),
                result.protocol(),
                result.status(),
                result.sourcePath(),
                result.targetPath(),
                result.fileName(),
                result.fileSizeBytes(),
                result.durationMillis(),
                result.errorMessage()
        );
    }

    /**
     * 將例外與 HTTP 狀態組裝為 TransmissionErrorOccurredResource
     *
     * @param status HTTP 狀態碼
     * @param message 錯誤詳細訊息
     * @param path 請求之 URI 路徑
     * @param exceptionName 例外類別簡稱
     * @return TransmissionErrorOccurredResource
     */
    public static TransmissionErrorOccurredResource toErrorResource(
            HttpStatus status,
            String message,
            String path,
            String exceptionName
    ) {
        return new TransmissionErrorOccurredResource(
                Instant.now().toString(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                exceptionName
        );
    }

    private static TransmissionProtocol parseProtocol(String protocolStr) {
        if (protocolStr == null || protocolStr.trim().isEmpty()) {
            return TransmissionProtocol.SFTP;
        }
        try {
            return TransmissionProtocol.valueOf(protocolStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支援的傳輸協定: " + protocolStr);
        }
    }
}
