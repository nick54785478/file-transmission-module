package com.example.demo.infrastructure.transmission.client.impl;

import com.example.demo.application.shared.exception.FileTransmissionException;
import com.example.demo.application.shared.exception.RemoteDirectoryCreationException;
import com.example.demo.application.shared.exception.TransmissionConnectionException;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFileInfo;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.demo.infrastructure.transmission.client.TransmissionProtocolClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.springframework.stereotype.Component;

/**
 * 標準 FTP (File Transfer Protocol) 協定傳輸客戶端實作 (FtpTransmissionClient)
 * <p>
 * <b>架構與設計核心：</b>
 * <ul>
 *   <li>將原先靜態且需外部傳入 {@link FTPClient} 的舊版 {@code FtpUtil} 重構為受 Spring 容器管理的無狀態元件。</li>
 *   <li><b>樣板回呼模式 (Template Callback Pattern)：</b> 透過 {@link #executeWithFtp(TransmissionServerConfig, FtpCallback)}
 *       統一管理連線建立、身分登入、傳輸模式設置、自動登出與連線釋放 (finally disconnect)，徹底防止 Socket 資源洩漏。</li>
 *   <li><b>串流管理安全性：</b> 傳輸讀寫全面採用 Java 7+ Try-with-resources 機制，確保本機檔案串流必定正常關閉，根除檔案被鎖定問題。</li>
 *   <li><b>雙向遞迴同步：</b> 支援「本機目錄至遠端」({@link SyncDirection#LOCAL_TO_REMOTE}) 與「遠端目錄至本機」({@link SyncDirection#REMOTE_TO_LOCAL})，
 *       並支援以時間戳記比對增量檔案與同步刪除遠端多餘檔案功能。</li>
 *   <li><b>遠端目錄容錯建立：</b> 內建逐層路徑解析與切換機制，若遠端上層目錄不存在則自動遞迴建立。</li>
 *   <li><b>例外轉譯機制：</b> 將 Apache Commons Net 的技術性 {@link IOException} 轉譯為統一的 {@link TransmissionConnectionException}
 *       與 {@link FileTransmissionException}。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class FtpTransmissionClient implements TransmissionProtocolClient {

    /**
     * 判定本客戶端是否支援指定的傳輸協定
     *
     * @param protocol 待檢核之傳輸協定列舉
     * @return 若協定為 {@link TransmissionProtocol#FTP} 則回傳 {@code true}，否則回傳 {@code false}
     */
    @Override
    public boolean supports(TransmissionProtocol protocol) {
        return TransmissionProtocol.FTP == protocol;
    }

    /**
     * 上傳單一檔案至遠端 FTP 伺服器指定目錄
     * <p>
     * 自動確認遠端目標目錄是否存在（若無則遞迴建立），並透過二進位串流將本機檔案寫入遠端。
     * </p>
     *
     * @param config 傳輸伺服器連線配置
     * @param localFilePath 本機來源檔案路徑模型
     * @param remotePath 遠端目標目錄路徑模型
     * @param filename 儲存於遠端之檔案名稱
     * @throws FileTransmissionException 當檔案存入失敗或 I/O 發生異常時拋出
     * @throws TransmissionConnectionException 當遠端連線建立或驗證失敗時拋出
     */
    @Override
    public void upload(TransmissionServerConfig config, LocalFilePath localFilePath, RemoteFilePath remotePath, String filename) {
        executeWithFtp(config, ftpClient -> {
            try {
                ensureRemoteDirectoryExists(ftpClient, remotePath.getValue());
                String targetFilePath = remotePath.getValue() + "/" + filename;

                File localFile = localFilePath.toFile();
                try (InputStream inputStream = new FileInputStream(localFile)) {
                    boolean success = ftpClient.storeFile(targetFilePath, inputStream);
                    if (!success) {
                        throw new FileTransmissionException("FTP 檔案存入失敗 (ReplyCode: " + ftpClient.getReplyCode() + ")");
                    }
                    log.info("[FTP] 上傳檔案成功: {} -> {}", localFilePath.getValue(), targetFilePath);
                }
                return null;
            } catch (IOException e) {
                log.error("[FTP] 上傳檔案發生異常: {}", e.getMessage(), e);
                throw new FileTransmissionException("FTP 上傳異常: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 從遠端 FTP 伺服器下載單一檔案至本機目標路徑
     * <p>
     * 自動檢查並建立本機父層目錄，透過二進位串流擷取遠端檔案內容寫入本機。
     * </p>
     *
     * @param config 傳輸伺服器連線配置
     * @param remoteFilePath 遠端來源檔案之完整路徑模型
     * @param savedPath 本機儲存之目標檔案路徑模型
     * @throws FileTransmissionException 當檔案擷取失敗或寫入異常時拋出
     * @throws TransmissionConnectionException 當連線建立失敗時拋出
     */
    @Override
    public void download(TransmissionServerConfig config, RemoteFilePath remoteFilePath, LocalFilePath savedPath) {
        executeWithFtp(config, ftpClient -> {
            try {
                File localTarget = savedPath.toFile();
                File parentDir = localTarget.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    boolean created = parentDir.mkdirs();
                    if (!created) {
                        log.warn("[FTP] 建立本機父目錄失敗: {}", parentDir.getAbsolutePath());
                    }
                }

                try (OutputStream outputStream = new FileOutputStream(localTarget)) {
                    boolean success = ftpClient.retrieveFile(remoteFilePath.getValue(), outputStream);
                    if (!success) {
                        throw new FileTransmissionException("FTP 檔案下載失敗 (ReplyCode: " + ftpClient.getReplyCode() + ")");
                    }
                    log.info("[FTP] 檔案下載成功: {} -> {}", remoteFilePath.getValue(), savedPath.getValue());
                }
                return null;
            } catch (IOException e) {
                log.error("[FTP] 下載檔案發生異常: {}", e.getMessage(), e);
                throw new FileTransmissionException("FTP 下載異常: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 執行本機目錄與遠端目錄的雙向檔案同步作業
     *
     * @param config 傳輸伺服器連線配置
     * @param localDir 本機目錄路徑模型
     * @param remoteDir 遠端目錄路徑模型
     * @param direction 同步傳輸方向 (本機至遠端 或 遠端至本機)
     * @param deleteRemoteExtras 若為本機至遠端，是否比對並刪除遠端多餘檔案
     * @return 實際傳輸成功的檔案總數
     * @throws FileTransmissionException 當同步過程發生 I/O 或操作失敗時拋出
     */
    @Override
    public int syncDirectory(
            TransmissionServerConfig config,
            LocalFilePath localDir,
            RemoteFilePath remoteDir,
            SyncDirection direction,
            boolean deleteRemoteExtras
    ) {
        return executeWithFtp(config, ftpClient -> {
            if (direction == SyncDirection.LOCAL_TO_REMOTE) {
                return syncLocalToRemoteInternal(ftpClient, localDir.toFile(), remoteDir.getValue(), deleteRemoteExtras);
            } else {
                return syncRemoteToLocalInternal(ftpClient, remoteDir.getValue(), localDir.toFile());
            }
        });
    }

    /**
     * 查詢遠端目錄下的檔案與子目錄清單元資料
     *
     * @param config 傳輸伺服器連線配置
     * @param remotePath 欲查詢的遠端目錄路徑模型
     * @return 遠端檔案中繼資料清單，已排除 "." 與 ".."
     * @throws FileTransmissionException 當查詢目錄清單發生錯誤時拋出
     */
    @Override
    public List<RemoteFileInfo> listFiles(TransmissionServerConfig config, RemoteFilePath remotePath) {
        return executeWithFtp(config, ftpClient -> {
            List<RemoteFileInfo> result = new ArrayList<>();
            try {
                FTPFile[] ftpFiles = ftpClient.listFiles(remotePath.getValue());
                if (ftpFiles != null) {
                    for (FTPFile file : ftpFiles) {
                        String name = file.getName();
                        if (".".equals(name) || "..".equals(name)) {
                            continue;
                        }
                        RemoteFilePath itemPath = remotePath.resolve(name);
                        long timestamp = (file.getTimestamp() != null) ? file.getTimestamp().getTimeInMillis() : 0L;
                        result.add(new RemoteFileInfo(
                                name,
                                itemPath,
                                file.getSize(),
                                timestamp,
                                file.isDirectory()
                        ));
                    }
                }
                return result;
            } catch (IOException e) {
                log.error("[FTP] 列出檔案清單失敗: {}", e.getMessage(), e);
                throw new FileTransmissionException("FTP 檔案清單查詢失敗: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 刪除遠端 FTP 伺服器上的指定單一檔案
     *
     * @param config 傳輸伺服器連線配置
     * @param remoteFilePath 欲刪除的遠端檔案路徑模型
     * @return 若刪除成功回傳 {@code true}，若檔案不存在或刪除失敗回傳 {@code false}
     */
    @Override
    public boolean deleteFile(TransmissionServerConfig config, RemoteFilePath remoteFilePath) {
        return executeWithFtp(config, ftpClient -> {
            try {
                boolean deleted = ftpClient.deleteFile(remoteFilePath.getValue());
                log.info("[FTP] 刪除遠端檔案: {} 結果: {}", remoteFilePath.getValue(), deleted);
                return deleted;
            } catch (IOException e) {
                log.error("[FTP] 刪除遠端檔案失敗: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * 遞迴刪除遠端 FTP 伺服器上的目錄及其內部所有子檔案與子目錄
     *
     * @param config 傳輸伺服器連線配置
     * @param remoteDirectoryPath 欲刪除的遠端目錄路徑模型
     * @return 若遞迴刪除成功回傳 {@code true}，否則回傳 {@code false}
     */
    @Override
    public boolean deleteDirectory(TransmissionServerConfig config, RemoteFilePath remoteDirectoryPath) {
        return executeWithFtp(config, ftpClient -> {
            try {
                deleteRemoteDirectoryRecursively(ftpClient, remoteDirectoryPath.getValue());
                return true;
            } catch (IOException e) {
                log.error("[FTP] 遞迴刪除遠端目錄失敗: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * 測試與遠端 FTP 伺服器的連線與登入狀態
     *
     * @param config 傳輸伺服器連線配置
     * @return 若成功連線並完成認證回傳 {@code true}，連線失敗或超時回傳 {@code false}
     */
    @Override
    public boolean testConnection(TransmissionServerConfig config) {
        try {
            executeWithFtp(config, ftpClient -> true);
            return true;
        } catch (Exception e) {
            log.warn("[FTP] 測試連線失敗: {}", e.getMessage());
            return false;
        }
    }

    // ================================= 內部輔助與重構邏輯 =================================

    /**
     * 內部遞迴同步實作：從本機目錄同步至遠端目錄
     *
     * @param ftpClient 已連線的 FTPClient
     * @param localDirectory 本機來源目錄
     * @param remoteDirPath 遠端目標目錄路徑字串
     * @param deleteRemoteExtras 是否刪除遠端多餘檔案
     * @return 傳輸檔案數量
     * @throws IOException 當傳輸或目錄操作發生 I/O 異常時拋出
     */
    private int syncLocalToRemoteInternal(
            FTPClient ftpClient,
            File localDirectory,
            String remoteDirPath,
            boolean deleteRemoteExtras
    ) throws IOException {
        if (!localDirectory.exists() || !localDirectory.isDirectory()) {
            throw new IllegalArgumentException("本機路徑非有效目錄: " + localDirectory.getAbsolutePath());
        }

        ensureRemoteDirectoryExists(ftpClient, remoteDirPath);
        File[] localFiles = localDirectory.listFiles();
        if (localFiles == null) {
            return 0;
        }

        FTPFile[] remoteFiles = ftpClient.listFiles(remoteDirPath);
        Set<String> remoteFileNames = new HashSet<>();
        if (remoteFiles != null) {
            for (FTPFile rf : remoteFiles) {
                remoteFileNames.add(rf.getName());
            }
        }

        int count = 0;
        for (File localFile : localFiles) {
            String name = localFile.getName();
            if (localFile.isDirectory()) {
                String subRemote = remoteDirPath + "/" + name;
                count += syncLocalToRemoteInternal(ftpClient, localFile, subRemote, deleteRemoteExtras);
            } else {
                if (!remoteFileNames.contains(name)) {
                    String targetFilePath = remoteDirPath + "/" + name;
                    try (InputStream is = new FileInputStream(localFile)) {
                        ftpClient.storeFile(targetFilePath, is);
                        count++;
                        log.info("[FTP] 同步檔案至遠端: {} -> {}", localFile.getAbsolutePath(), targetFilePath);
                    }
                }
            }
        }

        if (deleteRemoteExtras && remoteFiles != null) {
            Set<String> localNames = new HashSet<>(Arrays.asList(localDirectory.list()));
            for (FTPFile remoteFile : remoteFiles) {
                String name = remoteFile.getName();
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }
                if (!localNames.contains(name)) {
                    String remotePath = remoteDirPath + "/" + name;
                    if (remoteFile.isDirectory()) {
                        deleteRemoteDirectoryRecursively(ftpClient, remotePath);
                    } else {
                        ftpClient.deleteFile(remotePath);
                        log.warn("[FTP] 刪除遠端多餘檔案: {}", remotePath);
                    }
                }
            }
        }

        return count;
    }

    /**
     * 內部遞迴同步實作：從遠端目錄同步至本機目錄
     *
     * @param ftpClient 已連線的 FTPClient
     * @param remoteDirPath 遠端來源目錄路徑字串
     * @param localDirectory 本機目標目錄
     * @return 傳輸檔案數量
     * @throws IOException 當下載或目錄建立發生 I/O 異常時拋出
     */
    private int syncRemoteToLocalInternal(
            FTPClient ftpClient,
            String remoteDirPath,
            File localDirectory
    ) throws IOException {
        if (!localDirectory.exists()) {
            boolean created = localDirectory.mkdirs();
            if (!created) {
                log.warn("[FTP] 建立本機目錄失敗: {}", localDirectory.getAbsolutePath());
            }
        }

        int count = 0;
        FTPFile[] remoteFiles = ftpClient.listFiles(remoteDirPath);
        if (remoteFiles == null) {
            return 0;
        }

        for (FTPFile remoteFile : remoteFiles) {
            String name = remoteFile.getName();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }

            String subRemotePath = remoteDirPath + "/" + name;
            File subLocalFile = new File(localDirectory, name);

            if (remoteFile.isDirectory()) {
                count += syncRemoteToLocalInternal(ftpClient, subRemotePath, subLocalFile);
            } else {
                long remoteTime = (remoteFile.getTimestamp() != null) ? remoteFile.getTimestamp().getTimeInMillis() : 0L;
                if (!subLocalFile.exists() || subLocalFile.lastModified() < remoteTime) {
                    try (OutputStream os = new FileOutputStream(subLocalFile)) {
                        ftpClient.retrieveFile(subRemotePath, os);
                        if (remoteTime > 0) {
                            subLocalFile.setLastModified(remoteTime);
                        }
                        count++;
                        log.info("[FTP] 同步遠端檔案至本機: {} -> {}", subRemotePath, subLocalFile.getAbsolutePath());
                    }
                }
            }
        }

        return count;
    }

    /**
     * 確保遠端目標目錄存在，若不存在則沿路徑逐層遞迴建立
     *
     * @param ftpClient 已連線的 FTPClient
     * @param remotePath 欲檢查與建立的完整遠端路徑
     * @throws IOException 當切換或建立目錄失敗時拋出
     */
    private void ensureRemoteDirectoryExists(FTPClient ftpClient, String remotePath) throws IOException {
        String[] pathElements = remotePath.split("/");
        StringBuilder currentPath = new StringBuilder();

        for (String folder : pathElements) {
            if (folder.isEmpty()) {
                continue;
            }
            currentPath.append("/").append(folder);
            String directory = currentPath.toString();

            if (!ftpClient.changeWorkingDirectory(directory)) {
                boolean created = ftpClient.makeDirectory(directory);
                if (!created) {
                    throw new RemoteDirectoryCreationException("FTP 無法建立遠端目錄: " + directory);
                }
                ftpClient.changeWorkingDirectory(directory);
                log.info("[FTP] 建立遠端目錄成功: {}", directory);
            }
        }
    }

    /**
     * 遞迴刪除遠端目錄下的所有檔案與子目錄，最後刪除目錄本身
     *
     * @param ftpClient 已連線的 FTPClient
     * @param remoteDir 欲刪除的遠端目錄完整路徑
     * @throws IOException 當檔案或目錄刪除失敗時拋出
     */
    private void deleteRemoteDirectoryRecursively(FTPClient ftpClient, String remoteDir) throws IOException {
        FTPFile[] remoteFiles = ftpClient.listFiles(remoteDir);
        if (remoteFiles != null) {
            for (FTPFile remoteFile : remoteFiles) {
                String name = remoteFile.getName();
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }
                String subPath = remoteDir + "/" + name;
                if (remoteFile.isDirectory()) {
                    deleteRemoteDirectoryRecursively(ftpClient, subPath);
                } else {
                    ftpClient.deleteFile(subPath);
                }
            }
        }
        ftpClient.removeDirectory(remoteDir);
        log.warn("[FTP] 刪除遠端目錄成功: {}", remoteDir);
    }

    /**
     * 建立連線、身分驗證並初始化 FTPClient 客戶端
     *
     * @param config 傳輸伺服器連線配置
     * @return 已登入且完成模式切換的 FTPClient 實例
     * @throws IOException 當網路連線異常時拋出
     * @throws TransmissionConnectionException 當伺服器拒絕連線或帳號密碼登入失敗時拋出
     */
    protected FTPClient createAndConnectClient(TransmissionServerConfig config) throws IOException {
        FTPClient ftpClient = new FTPClient();
        ftpClient.setControlEncoding(StandardCharsets.UTF_8.name());
        ftpClient.setConnectTimeout(config.getTimeoutMs());
        ftpClient.setDataTimeout(config.getTimeoutMs());
        ftpClient.setDefaultTimeout(config.getTimeoutMs());
        ftpClient.setBufferSize(1024 * 1024);

        ftpClient.connect(config.getHost(), config.getPort());

        int replyCode = ftpClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(replyCode)) {
            ftpClient.disconnect();
            throw new TransmissionConnectionException("FTP 伺服器拒絕連線 (ReplyCode: " + replyCode + ")");
        }

        boolean loginSuccess = ftpClient.login(config.getUsername(), config.getPassword());
        if (!loginSuccess) {
            ftpClient.disconnect();
            throw new TransmissionConnectionException("FTP 登入失敗: 帳號或密碼錯誤 (user=" + config.getUsername() + ")");
        }

        // 依配置設定被動模式 (Passive Mode) 或主動模式 (Active Mode)
        if (config.isPassiveMode()) {
            ftpClient.enterLocalPassiveMode();
        } else {
            ftpClient.enterLocalActiveMode();
        }

        // 強制設定為二進位檔案模式，避免傳輸格式錯亂
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        return ftpClient;
    }

    /**
     * 執行 FTP 操作的樣板回呼方法，嚴格管理連線生命週期
     *
     * @param config 傳輸伺服器連線配置
     * @param callback 傳輸業務邏輯回呼
     * @param <T> 回傳值型別
     * @return 回呼執行結果
     * @throws TransmissionConnectionException 連線建立或認證異常時拋出
     * @throws FileTransmissionException 傳輸過程發生業務或 I/O 異常時拋出
     */
    private <T> T executeWithFtp(TransmissionServerConfig config, FtpCallback<T> callback) {
        FTPClient ftpClient = null;
        try {
            ftpClient = createAndConnectClient(config);
            return callback.doInFtp(ftpClient);
        } catch (IOException e) {
            log.error("[FTP] 連線或傳輸發生異常: {}", e.getMessage(), e);
            throw new TransmissionConnectionException("FTP 操作失敗: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[FTP] 執行操作時發生異常: {}", e.getMessage(), e);
            throw new FileTransmissionException("FTP 操作執行失敗: " + e.getMessage(), e);
        } finally {
            if (ftpClient != null && ftpClient.isConnected()) {
                try {
                    ftpClient.logout();
                } catch (IOException ignored) {
                }
                try {
                    ftpClient.disconnect();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * FTP 操作回呼函式介面 (Functional Interface)
     *
     * @param <T> 回傳型別
     */
    @FunctionalInterface
    private interface FtpCallback<T> {
        T doInFtp(FTPClient ftpClient) throws Exception;
    }
}
