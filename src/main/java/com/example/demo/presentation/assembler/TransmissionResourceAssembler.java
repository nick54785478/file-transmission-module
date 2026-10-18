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
import java.time.Instant;
import org.springframework.http.HttpStatus;

import com.example.demo.application.port.out.ServerCredentialStoragePort;

/**
 * 表現層資料組裝器 (TransmissionResourceAssembler - Anti-Corruption Layer)
 * <p>
 * 遵循六角形架構防腐層原則，隔離 HTTP 傳入的 Resource 與 Application 層 Command / Result。
 * 負責將 Request Resource 轉換為 UseCase Command，
 * 並將 UseCase 回傳的 Result DTO 轉換為 Response Resource。
 * 支援透過 {@link ServerCredentialStoragePort} 依憑證 ID/別名自動裝配連線配置。
 * </p>
 */
public class TransmissionResourceAssembler {

    /**
     * 將 UploadFileResource 請求載體轉換為 UploadFileCommand 應用命令
     */
    public static UploadFileCommand toCommand(UploadFileResource resource) {
        return toCommand(resource, null);
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
     * 將 UploadFileMultipartResource 與上傳檔案二進位資料轉換為 UploadFileFromMemoryCommand 應用命令
     */
    public static UploadFileFromMemoryCommand toCommand(
            UploadFileMultipartResource resource,
            byte[] fileContent,
            String originalFilename
    ) {
        return toCommand(resource, fileContent, originalFilename, null);
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
     * 將 DownloadFileResource 請求載體轉換為 DownloadFileCommand 應用命令
     */
    public static DownloadFileCommand toCommand(DownloadFileResource resource) {
        return toCommand(resource, null);
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
     * 將 SyncDirectoryResource 請求載體轉換為 SyncDirectoryCommand 應用命令
     */
    public static SyncDirectoryCommand toCommand(SyncDirectoryResource resource) {
        return toCommand(resource, null);
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
    public static FileUploadedResource toUploadedResource(FileTransmissionGottenResult result) {
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
    public static FileDownloadedResource toDownloadedResource(FileTransmissionGottenResult result) {
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
    public static DirectorySynchronizedResource toSynchronizedResource(DirectorySyncGottenResult result) {
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
    public static FileTransmissionSubmittedResource toSubmittedResource(FileTransmissionSubmittedResult result) {
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
    public static TransmissionStatusGottenResource toStatusResource(FileTransmissionGottenResult result) {
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
