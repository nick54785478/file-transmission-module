package com.example.demo.application.service;

import com.example.demo.application.assembler.TransmissionDtoAssembler;
import com.example.demo.application.port.in.DownloadFileUseCase;
import com.example.demo.application.port.in.SubmitDownloadFileUseCase;
import com.example.demo.application.port.in.SubmitSyncDirectoryUseCase;
import com.example.demo.application.port.in.SubmitUploadFileUseCase;
import com.example.demo.application.port.in.SyncDirectoryUseCase;
import com.example.demo.application.port.in.UploadFileFromMemoryUseCase;
import com.example.demo.application.port.in.UploadFileUseCase;
import com.example.demo.application.port.out.CredentialCipherPort;
import com.example.demo.application.port.out.FileTransmissionGatewayPort;
import com.example.demo.application.port.out.FileTransmissionTaskSchedulerPort;
import com.example.demo.application.port.out.FileTransmissionTrackerPort;
import com.example.demo.application.shared.command.DownloadFileCommand;
import com.example.demo.application.shared.command.SyncDirectoryCommand;
import com.example.demo.application.shared.command.UploadFileCommand;
import com.example.demo.application.shared.command.UploadFileFromMemoryCommand;
import com.example.demo.application.shared.dto.DirectorySyncGottenResult;
import com.example.demo.application.shared.dto.FileTransmissionGottenResult;
import com.example.demo.application.shared.dto.FileTransmissionSubmittedResult;
import com.example.demo.application.shared.exception.FileTransmissionException;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 檔案傳輸命令服務 (FileTransmissionCommandService - Inbound Adapter / Application Service)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>in : ... + CommandService</code> (AGENTS.md 3-9 CQRS)。</li>
 *   <li>實作同步 Inbound Ports: {@link UploadFileUseCase}, {@link DownloadFileUseCase}, {@link SyncDirectoryUseCase}。</li>
 *   <li>實作非同步 Inbound Ports: {@link SubmitUploadFileUseCase}, {@link SubmitDownloadFileUseCase}, {@link SubmitSyncDirectoryUseCase}。</li>
 *   <li><b>純淨應用層協調者：</b> 本服務專注於業務使用案例 (Use Cases) 的流程調度，
 *       透過 <code>CredentialCipherPort</code> 執行命令中機密憑證的解密還原，
 *       透過 <code>FileTransmissionGatewayPort</code> 執行遠端傳輸閘道協定操作，
 *       透過 <code>FileTransmissionTrackerPort</code> 委派傳輸作業全程生命週期追蹤 (Tracker) 與審計紀錄，
 *       透過 <code>FileTransmissionTaskSchedulerPort</code> 輸出埠委派長任務背景非同步排程，達成 100% 零技術框架侵入。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class FileTransmissionCommandService implements
        UploadFileUseCase,
        UploadFileFromMemoryUseCase,
        DownloadFileUseCase,
        SyncDirectoryUseCase,
        SubmitUploadFileUseCase,
        SubmitDownloadFileUseCase,
        SubmitSyncDirectoryUseCase {

    private final FileTransmissionGatewayPort fileTransmissionGatewayPort;
    private final FileTransmissionTrackerPort fileTransmissionTrackerPort;
    private final FileTransmissionTaskSchedulerPort fileTransmissionTaskSchedulerPort;
    private final CredentialCipherPort credentialCipherPort;

    /**
     * 向下相容建構子 (3 參數版本)：維持既有單元測試完全相容
     *
     * @param fileTransmissionGatewayPort 檔案傳輸通訊閘道輸出埠
     * @param fileTransmissionTrackerPort 檔案傳輸生命週期追蹤輸出埠
     * @param fileTransmissionTaskSchedulerPort 檔案傳輸任務非同步排程輸出埠
     */
    public FileTransmissionCommandService(
            FileTransmissionGatewayPort fileTransmissionGatewayPort,
            FileTransmissionTrackerPort fileTransmissionTrackerPort,
            FileTransmissionTaskSchedulerPort fileTransmissionTaskSchedulerPort
    ) {
        this(fileTransmissionGatewayPort, fileTransmissionTrackerPort, fileTransmissionTaskSchedulerPort, CredentialCipherPort.NOOP);
    }

    /**
     * 建構檔案傳輸命令應用服務 (完整依賴注入版本，供 Spring 容器自動裝配)
     *
     * @param fileTransmissionGatewayPort 檔案傳輸通訊閘道輸出埠
     * @param fileTransmissionTrackerPort 檔案傳輸生命週期追蹤輸出埠
     * @param fileTransmissionTaskSchedulerPort 檔案傳輸任務非同步排程輸出埠
     * @param credentialCipherPort 憑證機密加解密輸出埠
     */
    @Autowired
    public FileTransmissionCommandService(
            FileTransmissionGatewayPort fileTransmissionGatewayPort,
            FileTransmissionTrackerPort fileTransmissionTrackerPort,
            FileTransmissionTaskSchedulerPort fileTransmissionTaskSchedulerPort,
            CredentialCipherPort credentialCipherPort
    ) {
        this.fileTransmissionGatewayPort = Objects.requireNonNull(fileTransmissionGatewayPort, "fileTransmissionGatewayPort 不能為空");
        this.fileTransmissionTrackerPort = Objects.requireNonNull(fileTransmissionTrackerPort, "fileTransmissionTrackerPort 不能為空");
        this.fileTransmissionTaskSchedulerPort = Objects.requireNonNull(fileTransmissionTaskSchedulerPort, "fileTransmissionTaskSchedulerPort 不能為空");
        this.credentialCipherPort = Objects.requireNonNull(credentialCipherPort, "credentialCipherPort 不能為空");
    }

    @Override
    public FileTransmissionGottenResult uploadFile(UploadFileCommand command) {
        // 對傳輸伺服器配置中的機密憑證 (登入密碼與 SSH 私鑰 Passphrase) 進行解密
        TransmissionServerConfig effectiveConfig = credentialCipherPort.decryptServerConfig(command.serverConfig());
        String trackingId = fileTransmissionTrackerPort.startTracking(effectiveConfig);

        try {
            LocalFilePath localFile = command.localFilePath();
            if (!localFile.exists() || !localFile.isFile()) {
                log.error("本機上傳來源檔案不存在或非一般檔案: {}", localFile.getValue());
                throw new FileTransmissionException("本機上傳來源檔案不存在或非一般檔案: " + localFile.getValue());
            }

            long fileSize = localFile.toFile().length();
            String fileName = (command.filename() != null && !command.filename().trim().isEmpty())
                    ? command.filename().trim()
                    : localFile.getFileName();

            // 依據方案 A 策略：若遠端目標檔案已存在，先自動將既有檔案歸檔備份至 .bak，確保最新版維持標準檔名
            RemoteFilePath targetFilePath = command.remotePath().resolve(fileName);
            archiveExistingRemoteFile(effectiveConfig, targetFilePath);

            // 呼叫 Outbound Port 執行實際通訊閘道傳輸
            fileTransmissionGatewayPort.upload(effectiveConfig, localFile, command.remotePath(), fileName);

            // 透過 Outbound Port 記錄追蹤成功並獲取傳輸結果 DTO
            return fileTransmissionTrackerPort.recordUploadSuccess(
                    trackingId,
                    localFile,
                    command.remotePath(),
                    fileName,
                    fileSize
            );
        } catch (Exception ex) {
            fileTransmissionTrackerPort.recordFailure(trackingId, ex.getMessage());
            throw new FileTransmissionException("上傳檔案失敗: " + ex.getMessage(), ex);
        }
    }

    @Override
    public FileTransmissionGottenResult uploadFileFromMemory(UploadFileFromMemoryCommand command) {
        TransmissionServerConfig effectiveConfig = credentialCipherPort.decryptServerConfig(command.serverConfig());
        String trackingId = fileTransmissionTrackerPort.startTracking(effectiveConfig);

        try {
            byte[] content = command.fileContent();
            long fileSize = content.length;
            String fileName = command.filename();

            // 若遠端目標檔案已存在，先自動將既有檔案歸檔備份至 .bak，確保最新版維持標準檔名
            RemoteFilePath targetFilePath = command.remotePath().resolve(fileName);
            archiveExistingRemoteFile(effectiveConfig, targetFilePath);

            // 呼叫 Outbound Port 執行實際通訊閘道傳輸 (Zero Disk I/O 記憶體直傳)
            fileTransmissionGatewayPort.uploadFromMemory(
                    effectiveConfig,
                    content,
                    command.remotePath(),
                    fileName
            );

            // 透過 Outbound Port 記錄追蹤成功並獲取傳輸結果 DTO
            return fileTransmissionTrackerPort.recordUploadSuccess(
                    trackingId,
                    null,
                    command.remotePath(),
                    fileName,
                    fileSize
            );
        } catch (Exception ex) {
            fileTransmissionTrackerPort.recordFailure(trackingId, ex.getMessage());
            throw new FileTransmissionException("記憶體檔案上傳失敗: " + ex.getMessage(), ex);
        }
    }

    @Override
    public FileTransmissionSubmittedResult submitUploadFile(UploadFileCommand command) {
        TransmissionServerConfig effectiveConfig = credentialCipherPort.decryptServerConfig(command.serverConfig());
        String trackingId = fileTransmissionTrackerPort.startTracking(effectiveConfig);

        fileTransmissionTaskSchedulerPort.schedule(() -> {
            try {
                LocalFilePath localFile = command.localFilePath();
                if (!localFile.exists() || !localFile.isFile()) {
                    throw new FileTransmissionException("本機上傳來源檔案不存在或非一般檔案: " + localFile.getValue());
                }

                long fileSize = localFile.toFile().length();
                String fileName = (command.filename() != null && !command.filename().trim().isEmpty())
                        ? command.filename().trim()
                        : localFile.getFileName();

                // 依據方案 A 策略：若遠端目標檔案已存在，先自動將既有檔案歸檔備份至 .bak，確保最新版維持標準檔名
                RemoteFilePath targetFilePath = command.remotePath().resolve(fileName);
                archiveExistingRemoteFile(effectiveConfig, targetFilePath);

                // 上傳本機檔案至遠端伺服器指定目錄
                fileTransmissionGatewayPort.upload(effectiveConfig, localFile, command.remotePath(), fileName);
                fileTransmissionTrackerPort.recordUploadSuccess(trackingId, localFile, command.remotePath(), fileName, fileSize);
            } catch (Exception ex) {
                fileTransmissionTrackerPort.recordFailure(trackingId, ex.getMessage());
            }
        });

        return TransmissionDtoAssembler.toSubmittedResult(
                trackingId,
                effectiveConfig.getProtocol().name(),
                "IN_PROGRESS",
                "檔案上傳任務已提交至背景執行緒執行"
        );
    }

    @Override
    public FileTransmissionGottenResult downloadFile(DownloadFileCommand command) {
        TransmissionServerConfig effectiveConfig = credentialCipherPort.decryptServerConfig(command.serverConfig());
        String trackingId = fileTransmissionTrackerPort.startTracking(effectiveConfig);

        try {
            RemoteFilePath remoteFilePath = command.remoteFilePath();
            LocalFilePath savedPath = command.savedPath();

            // 呼叫 Outbound Port 執行下載閘道操作
            fileTransmissionGatewayPort.download(effectiveConfig, remoteFilePath, savedPath);

            long downloadedSize = savedPath.exists() ? savedPath.toFile().length() : 0L;
            String fileName = remoteFilePath.getFileName();

            // 透過 Outbound Port 記錄追蹤成功並獲取傳輸結果 DTO
            return fileTransmissionTrackerPort.recordDownloadSuccess(
                    trackingId,
                    remoteFilePath,
                    savedPath,
                    fileName,
                    downloadedSize
            );
        } catch (Exception ex) {
            fileTransmissionTrackerPort.recordFailure(trackingId, ex.getMessage());
            throw new FileTransmissionException("下載檔案失敗: " + ex.getMessage(), ex);
        }
    }

    @Override
    public FileTransmissionSubmittedResult submitDownloadFile(DownloadFileCommand command) {
        TransmissionServerConfig effectiveConfig = credentialCipherPort.decryptServerConfig(command.serverConfig());
        String trackingId = fileTransmissionTrackerPort.startTracking(effectiveConfig);

        fileTransmissionTaskSchedulerPort.schedule(() -> {
            try {
                RemoteFilePath remoteFilePath = command.remoteFilePath();
                LocalFilePath savedPath = command.savedPath();

                fileTransmissionGatewayPort.download(effectiveConfig, remoteFilePath, savedPath);

                long downloadedSize = savedPath.exists() ? savedPath.toFile().length() : 0L;
                String fileName = remoteFilePath.getFileName();

                fileTransmissionTrackerPort.recordDownloadSuccess(trackingId, remoteFilePath, savedPath, fileName, downloadedSize);
            } catch (Exception ex) {
                fileTransmissionTrackerPort.recordFailure(trackingId, ex.getMessage());
            }
        });

        return TransmissionDtoAssembler.toSubmittedResult(
                trackingId,
                effectiveConfig.getProtocol().name(),
                "IN_PROGRESS",
                "檔案下載任務已提交至背景執行緒執行"
        );
    }

    @Override
    public DirectorySyncGottenResult syncDirectory(SyncDirectoryCommand command) {
        TransmissionServerConfig effectiveConfig = credentialCipherPort.decryptServerConfig(command.serverConfig());
        String trackingId = fileTransmissionTrackerPort.startTracking(effectiveConfig);

        try {
            LocalFilePath localDir = command.localDir();
            RemoteFilePath remoteDir = command.remoteDir();
            SyncDirection direction = command.direction();

            int transferredCount = fileTransmissionGatewayPort.syncDirectory(
                    effectiveConfig,
                    localDir,
                    remoteDir,
                    direction,
                    command.deleteRemoteExtras()
            );

            // 透過 Outbound Port 記錄追蹤成功並獲取同步結果 DTO
            return fileTransmissionTrackerPort.recordSyncSuccess(
                    trackingId,
                    direction,
                    localDir,
                    remoteDir,
                    transferredCount,
                    0
            );
        } catch (Exception ex) {
            fileTransmissionTrackerPort.recordFailure(trackingId, ex.getMessage());
            throw new FileTransmissionException("目錄同步失敗: " + ex.getMessage(), ex);
        }
    }

    @Override
    public FileTransmissionSubmittedResult submitSyncDirectory(SyncDirectoryCommand command) {
        TransmissionServerConfig effectiveConfig = credentialCipherPort.decryptServerConfig(command.serverConfig());
        String trackingId = fileTransmissionTrackerPort.startTracking(effectiveConfig);

        fileTransmissionTaskSchedulerPort.schedule(() -> {
            try {
                LocalFilePath localDir = command.localDir();
                RemoteFilePath remoteDir = command.remoteDir();
                SyncDirection direction = command.direction();

                int transferredCount = fileTransmissionGatewayPort.syncDirectory(
                        effectiveConfig,
                        localDir,
                        remoteDir,
                        direction,
                        command.deleteRemoteExtras()
                );

                // 記錄目錄同步成功並產生對應的目錄同步結果資料載體
                fileTransmissionTrackerPort.recordSyncSuccess(trackingId, direction, localDir, remoteDir, transferredCount, 0);
            } catch (Exception ex) {
                fileTransmissionTrackerPort.recordFailure(trackingId, ex.getMessage());
            }
        });

        return TransmissionDtoAssembler.toSubmittedResult(
                trackingId,
                effectiveConfig.getProtocol().name(),
                "IN_PROGRESS",
                "目錄同步任務已提交至背景執行緒執行"
        );
    }

    /**
     * 依據方案 A (標準最新版維護原名 + 舊版本自動歷史歸檔) 策略：
     * 若遠端目標檔案已存在，則先將既有遠端檔案移動備份為 timestamp .bak 檔案，確保最新版永遠保有名稱且歷史版本不流失。
     *
     * @param config 傳輸伺服器配置模型
     * @param targetFilePath 遠端目標檔案完整路徑模型
     */
    private void archiveExistingRemoteFile(TransmissionServerConfig config, RemoteFilePath targetFilePath) {
        if (fileTransmissionGatewayPort.existsFile(config, targetFilePath)) {
            RemoteFilePath backupPath = targetFilePath.toBackupPath();
            fileTransmissionGatewayPort.moveFile(config, targetFilePath, backupPath);
        }
    }
}
