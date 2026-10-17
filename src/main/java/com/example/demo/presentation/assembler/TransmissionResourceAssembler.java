package com.example.demo.presentation.assembler;

import com.example.demo.application.shared.command.DownloadFileCommand;
import com.example.demo.application.shared.command.SyncDirectoryCommand;
import com.example.demo.application.shared.command.UploadFileCommand;
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
import com.example.demo.presentation.resource.in.UploadFileResource;
import com.example.demo.presentation.resource.out.DirectorySynchronizedResource;
import com.example.demo.presentation.resource.out.FileDownloadedResource;
import com.example.demo.presentation.resource.out.FileTransmissionSubmittedResource;
import com.example.demo.presentation.resource.out.FileUploadedResource;
import com.example.demo.presentation.resource.out.TransmissionErrorOccurredResource;
import com.example.demo.presentation.resource.out.TransmissionStatusGottenResource;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/**
 * 表現層資料組裝器 (TransmissionResourceAssembler - Anti-Corruption Layer)
 * <p>
 * 遵循六角形架構防腐層原則，隔離 HTTP 傳入的 Resource 與 Application 層 Command / Result。
 * 負責將 Request Resource 轉換為 UseCase Command，
 * 並將 UseCase 回傳的 Result DTO 轉換為 Response Resource。
 * </p>
 */
public class TransmissionResourceAssembler {

    /**
     * 將 UploadFileResource 請求載體轉換為 UploadFileCommand 應用命令
     *
     * @param resource 表現層請求載體
     * @return UploadFileCommand
     */
    public static UploadFileCommand toCommand(UploadFileResource resource) {
        TransmissionProtocol protocol = parseProtocol(resource.protocol());
        TransmissionServerConfig serverConfig = new TransmissionServerConfig(
                resource.host(),
                resource.port(),
                protocol,
                resource.username(),
                resource.password(),
                resource.timeoutMs(),
                resource.passiveMode() != null && resource.passiveMode(),
                resource.strictHostKeyChecking() != null && resource.strictHostKeyChecking(),
                resource.privateKey(),
                resource.passphrase()
        );

        return new UploadFileCommand(
                serverConfig,
                LocalFilePath.of(resource.localFilePath()),
                RemoteFilePath.of(resource.remotePath()),
                resource.filename()
        );
    }

    /**
     * 將 DownloadFileResource 請求載體轉換為 DownloadFileCommand 應用命令
     *
     * @param resource 表現層請求載體
     * @return DownloadFileCommand
     */
    public static DownloadFileCommand toCommand(DownloadFileResource resource) {
        TransmissionProtocol protocol = parseProtocol(resource.protocol());
        TransmissionServerConfig serverConfig = new TransmissionServerConfig(
                resource.host(),
                resource.port(),
                protocol,
                resource.username(),
                resource.password(),
                resource.timeoutMs(),
                resource.passiveMode() != null && resource.passiveMode(),
                resource.strictHostKeyChecking() != null && resource.strictHostKeyChecking(),
                resource.privateKey(),
                resource.passphrase()
        );

        return new DownloadFileCommand(
                serverConfig,
                RemoteFilePath.of(resource.remoteFilePath()),
                LocalFilePath.of(resource.savedLocalPath())
        );
    }

    /**
     * 將 SyncDirectoryResource 請求載體轉換為 SyncDirectoryCommand 應用命令
     *
     * @param resource 表現層請求載體
     * @return SyncDirectoryCommand
     */
    public static SyncDirectoryCommand toCommand(SyncDirectoryResource resource) {
        TransmissionProtocol protocol = parseProtocol(resource.protocol());
        TransmissionServerConfig serverConfig = new TransmissionServerConfig(
                resource.host(),
                resource.port(),
                protocol,
                resource.username(),
                resource.password(),
                resource.timeoutMs(),
                resource.passiveMode() != null && resource.passiveMode(),
                resource.strictHostKeyChecking() != null && resource.strictHostKeyChecking(),
                resource.privateKey(),
                resource.passphrase()
        );

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
