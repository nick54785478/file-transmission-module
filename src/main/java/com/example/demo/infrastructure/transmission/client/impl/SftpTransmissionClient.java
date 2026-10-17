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
import com.example.demo.infrastructure.transmission.client.TransmissionProtocolClient;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SFTP (SSH File Transfer Protocol) 安全協定傳輸客戶端實作 (SftpTransmissionClient)
 * <p>
 * <b>架構與設計核心：</b>
 * <ul>
 *   <li>將原先包含靜態變數 (static Session / static ChannelSftp) 的舊版 {@code SftpUtil} 徹底重構為受 Spring 容器管理的標準客戶端元件。</li>
 *   <li><b>消除並發衝突：</b> 徹底拔除所有靜態連線物件，多執行緒同時呼叫時各自擁有獨立且隔離的 SSH Session 與 Channel，確保執行緒安全 (Thread-Safe)。</li>
 *   <li><b>樣板方法模式 (Template Callback Pattern)：</b> 透過 {@link #executeWithSftp(TransmissionServerConfig, SftpCallback)}
 *       嚴密管理 SSH Session 與 ChannelSftp 之建立、認證、連線設定與釋放 (finally disconnect)，杜絕連線懸掛與 SSH 連線數洩漏。</li>
 *   <li><b>雙向遞迴目錄同步：</b> 完整支援「本機至遠端」與「遠端至本機」之目錄同步作業，並依據檔案修改時間 (mtime) 進行增量智慧比對與傳輸。</li>
 *   <li><b>遠端目錄容錯遞迴建立：</b> 內建路徑逐層測試與建立機制 (mkdir & cd)，在遠端目標多層目錄不存在時自動完整補齊。</li>
 *   <li><b>例外轉譯機制：</b> 將 JSch 之底層技術例外 (如 {@link JSchException}、{@link SftpException}) 封裝轉換為統一的傳輸領域例外。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class SftpTransmissionClient implements TransmissionProtocolClient {

    /**
     * 判定本客戶端是否支援指定的傳輸協定
     *
     * @param protocol 待檢核之傳輸協定列舉
     * @return 若協定為 {@link TransmissionProtocol#SFTP} 則回傳 {@code true}，否則回傳 {@code false}
     */
    @Override
    public boolean supports(TransmissionProtocol protocol) {
        return TransmissionProtocol.SFTP == protocol;
    }

    /**
     * 上傳單一檔案至遠端 SFTP 伺服器指定目錄
     * <p>
     * 自動確認並建立遠端目標目錄，切換目錄後透過串流完成寫入上傳。
     * </p>
     *
     * @param config 傳輸伺服器連線配置
     * @param localFilePath 本機來源檔案路徑模型
     * @param remotePath 遠端目標目錄路徑模型
     * @param filename 儲存於遠端之檔案名稱
     * @throws FileTransmissionException 當上傳過程發生 SFTP 協定錯誤或本機 I/O 異常時拋出
     * @throws TransmissionConnectionException 當 SSH 連線建立或認證失敗時拋出
     */
    @Override
    public void upload(TransmissionServerConfig config, LocalFilePath localFilePath, RemoteFilePath remotePath, String filename) {
        executeWithSftp(config, sftp -> {
            try {
                ensureRemoteDirectoryExists(sftp, remotePath.getValue());
                sftp.cd(remotePath.getValue());

                File localFile = localFilePath.toFile();
                try (InputStream inputStream = new FileInputStream(localFile)) {
                    sftp.put(inputStream, filename);
                    log.info("[SFTP] 檔案上傳成功: {} -> {}/{}", localFilePath.getValue(), remotePath.getValue(), filename);
                }
                return null;
            } catch (SftpException | IOException e) {
                log.error("[SFTP] 檔案上傳發生異常: {}", e.getMessage(), e);
                throw new FileTransmissionException("SFTP 上傳失敗: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 從遠端 SFTP 伺服器下載單一檔案至本機目標路徑
     * <p>
     * 自動檢查並建立本機父層目錄，透過 SFTP 串流下載並儲存檔案。
     * </p>
     *
     * @param config 傳輸伺服器連線配置
     * @param remoteFilePath 遠端來源檔案之完整路徑模型
     * @param savedPath 本機儲存之目標檔案路徑模型
     * @throws FileTransmissionException 當檔案下載失敗或寫入本機異常時拋出
     * @throws TransmissionConnectionException 當 SSH 連線建立失敗時拋出
     */
    @Override
    public void download(TransmissionServerConfig config, RemoteFilePath remoteFilePath, LocalFilePath savedPath) {
        executeWithSftp(config, sftp -> {
            try {
                File localTarget = savedPath.toFile();
                File parentDir = localTarget.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    boolean created = parentDir.mkdirs();
                    if (!created) {
                        log.warn("[SFTP] 建立本機目錄失敗: {}", parentDir.getAbsolutePath());
                    }
                }

                try (OutputStream outputStream = new FileOutputStream(localTarget)) {
                    sftp.get(remoteFilePath.getValue(), outputStream);
                    log.info("[SFTP] 檔案下載成功: {} -> {}", remoteFilePath.getValue(), savedPath.getValue());
                }
                return null;
            } catch (SftpException | IOException e) {
                log.error("[SFTP] 檔案下載發生異常: {}", e.getMessage(), e);
                throw new FileTransmissionException("SFTP 下載失敗: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 執行本機目錄與遠端 SFTP 目錄的雙向同步作業
     *
     * @param config 傳輸伺服器連線配置
     * @param localDir 本機目錄路徑模型
     * @param remoteDir 遠端目錄路徑模型
     * @param direction 同步傳輸方向 (本機至遠端 或 遠端至本機)
     * @param deleteRemoteExtras 是否刪除遠端多餘檔案 (SFTP 同步保留擴充參數)
     * @return 實際傳輸成功的檔案總數
     * @throws FileTransmissionException 當同步過程發生協定錯誤或 I/O 異常時拋出
     */
    @Override
    public int syncDirectory(
            TransmissionServerConfig config,
            LocalFilePath localDir,
            RemoteFilePath remoteDir,
            SyncDirection direction,
            boolean deleteRemoteExtras
    ) {
        return executeWithSftp(config, sftp -> {
            if (direction == SyncDirection.LOCAL_TO_REMOTE) {
                return syncLocalToRemoteInternal(sftp, localDir.toFile(), remoteDir.getValue());
            } else {
                return syncRemoteToLocalInternal(sftp, remoteDir.getValue(), localDir.toFile());
            }
        });
    }

    /**
     * 查詢遠端 SFTP 目錄下的檔案與子目錄清單元資料
     *
     * @param config 傳輸伺服器連線配置
     * @param remotePath 欲查詢的遠端目錄路徑模型
     * @return 遠端檔案中繼資料清單，已排除 "." 與 ".."
     * @throws FileTransmissionException 當查詢目錄發生錯誤時拋出
     */
    @Override
    public List<RemoteFileInfo> listFiles(TransmissionServerConfig config, RemoteFilePath remotePath) {
        return executeWithSftp(config, sftp -> {
            List<RemoteFileInfo> result = new ArrayList<>();
            try {
                @SuppressWarnings("unchecked")
                Vector<ChannelSftp.LsEntry> entries = sftp.ls(remotePath.getValue());
                for (ChannelSftp.LsEntry entry : entries) {
                    String name = entry.getFilename();
                    if (".".equals(name) || "..".equals(name)) {
                        continue;
                    }
                    SftpATTRS attrs = entry.getAttrs();
                    RemoteFilePath itemPath = remotePath.resolve(name);
                    result.add(new RemoteFileInfo(
                            name,
                            itemPath,
                            attrs.getSize(),
                            attrs.getMTime() * 1000L,
                            attrs.isDir()
                    ));
                }
                return result;
            } catch (SftpException e) {
                log.error("[SFTP] 查詢遠端目錄失敗: {}", e.getMessage(), e);
                throw new FileTransmissionException("SFTP 查詢遠端目錄失敗: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 刪除遠端 SFTP 伺服器上的指定單一檔案
     *
     * @param config 傳輸伺服器連線配置
     * @param remoteFilePath 欲刪除的遠端檔案路徑模型
     * @return 若刪除成功回傳 {@code true}，檔案不存在或失敗回傳 {@code false}
     */
    @Override
    public boolean deleteFile(TransmissionServerConfig config, RemoteFilePath remoteFilePath) {
        return executeWithSftp(config, sftp -> {
            try {
                sftp.rm(remoteFilePath.getValue());
                log.info("[SFTP] 刪除遠端檔案成功: {}", remoteFilePath.getValue());
                return true;
            } catch (SftpException e) {
                log.error("[SFTP] 刪除遠端檔案失敗: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * 遞迴刪除遠端 SFTP 伺服器上的目錄及其所有子目錄與子檔案
     *
     * @param config 傳輸伺服器連線配置
     * @param remoteDirectoryPath 欲刪除的遠端目錄路徑模型
     * @return 若遞迴刪除成功回傳 {@code true}，否則回傳 {@code false}
     */
    @Override
    public boolean deleteDirectory(TransmissionServerConfig config, RemoteFilePath remoteDirectoryPath) {
        return executeWithSftp(config, sftp -> {
            try {
                deleteRemoteDirectoryRecursively(sftp, remoteDirectoryPath.getValue());
                return true;
            } catch (SftpException e) {
                log.error("[SFTP] 刪除遠端目錄失敗: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * 測試與遠端 SFTP 伺服器的 SSH 連線與驗證狀態
     *
     * @param config 傳輸伺服器連線配置
     * @return 若成功連線並完成 SSH 握手與認證回傳 {@code true}，失敗回傳 {@code false}
     */
    @Override
    public boolean testConnection(TransmissionServerConfig config) {
        try {
            executeWithSftp(config, sftp -> true);
            return true;
        } catch (Exception e) {
            log.warn("[SFTP] 測試連線失敗: {}", e.getMessage());
            return false;
        }
    }

    // ================================= 內部輔助與重構邏輯 =================================

    /**
     * 內部遞迴同步實作：從本機目錄同步至遠端 SFTP 目錄
     *
     * @param sftp 已連線之 ChannelSftp
     * @param localDirectory 本機來源目錄
     * @param remoteDirPath 遠端目標目錄完整路徑
     * @return 傳輸成功檔案總數
     */
    private int syncLocalToRemoteInternal(ChannelSftp sftp, File localDirectory, String remoteDirPath) {
        if (!localDirectory.exists() || !localDirectory.isDirectory()) {
            throw new IllegalArgumentException("本機路徑非有效目錄: " + localDirectory.getAbsolutePath());
        }

        int count = 0;
        try {
            ensureRemoteDirectoryExists(sftp, remoteDirPath);
            File[] localFiles = localDirectory.listFiles();
            if (localFiles == null) {
                return 0;
            }

            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> remoteEntries = sftp.ls(remoteDirPath);

            for (File file : localFiles) {
                if (file.isFile()) {
                    boolean needsUpload = true;
                    for (ChannelSftp.LsEntry entry : remoteEntries) {
                        if (entry.getFilename().equals(file.getName())) {
                            // 若遠端檔案最後修改時間大於等於本機檔案，則跳過上傳
                            if (file.lastModified() <= entry.getAttrs().getMTime() * 1000L) {
                                needsUpload = false;
                            }
                            break;
                        }
                    }

                    if (needsUpload) {
                        try (InputStream is = new FileInputStream(file)) {
                            sftp.cd(remoteDirPath);
                            sftp.put(is, file.getName());
                            count++;
                            log.info("[SFTP] 同步檔案至遠端: {} -> {}", file.getAbsolutePath(), remoteDirPath);
                        }
                    }
                } else if (file.isDirectory()) {
                    String subRemoteDir = remoteDirPath + "/" + file.getName();
                    count += syncLocalToRemoteInternal(sftp, file, subRemoteDir);
                }
            }
        } catch (SftpException | IOException e) {
            throw new FileTransmissionException("SFTP 本機同步至遠端異常: " + e.getMessage(), e);
        }
        return count;
    }

    /**
     * 內部遞迴同步實作：從遠端 SFTP 目錄同步至本機目錄
     *
     * @param sftp 已連線之 ChannelSftp
     * @param remoteDirPath 遠端來源目錄完整路徑
     * @param localDirectory 本機目標目錄
     * @return 傳輸成功檔案總數
     */
    private int syncRemoteToLocalInternal(ChannelSftp sftp, String remoteDirPath, File localDirectory) {
        if (!localDirectory.exists()) {
            boolean created = localDirectory.mkdirs();
            if (!created) {
                log.warn("[SFTP] 建立本機目錄失敗: {}", localDirectory.getAbsolutePath());
            }
        }

        int count = 0;
        try {
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> remoteEntries = sftp.ls(remoteDirPath);
            for (ChannelSftp.LsEntry entry : remoteEntries) {
                String entryName = entry.getFilename();
                if (".".equals(entryName) || "..".equals(entryName)) {
                    continue;
                }

                String subRemotePath = remoteDirPath + "/" + entryName;
                File subLocalFile = new File(localDirectory, entryName);

                if (entry.getAttrs().isDir()) {
                    count += syncRemoteToLocalInternal(sftp, subRemotePath, subLocalFile);
                } else {
                    long remoteTime = entry.getAttrs().getMTime() * 1000L;
                    // 若本機檔案不存在或本機修改時間早於遠端，則進行增量下載
                    if (!subLocalFile.exists() || subLocalFile.lastModified() < remoteTime) {
                        try (OutputStream os = new FileOutputStream(subLocalFile)) {
                            sftp.get(subRemotePath, os);
                            subLocalFile.setLastModified(remoteTime);
                            count++;
                            log.info("[SFTP] 同步檔案至本機: {} -> {}", subRemotePath, subLocalFile.getAbsolutePath());
                        }
                    }
                }
            }
        } catch (SftpException | IOException e) {
            throw new FileTransmissionException("SFTP 遠端同步至本機異常: " + e.getMessage(), e);
        }
        return count;
    }

    /**
     * 確保遠端目標目錄存在，若不存在則沿路徑逐層遞迴建立
     *
     * @param sftp 已連線之 ChannelSftp
     * @param remotePath 欲檢查與建立的遠端目錄路徑
     * @throws SftpException 當遠端目錄操作發生協定異常時拋出
     */
    private void ensureRemoteDirectoryExists(ChannelSftp sftp, String remotePath) throws SftpException {
        try {
            sftp.cd(remotePath);
        } catch (SftpException e) {
            String[] directories = remotePath.split("/");
            StringBuilder currentPath = new StringBuilder();
            for (String dir : directories) {
                if (dir.isEmpty()) {
                    continue;
                }
                currentPath.append("/").append(dir);
                String pathStr = currentPath.toString();
                try {
                    sftp.cd(pathStr);
                } catch (SftpException ex) {
                    try {
                        sftp.mkdir(pathStr);
                        sftp.cd(pathStr);
                    } catch (SftpException creationError) {
                        throw new RemoteDirectoryCreationException("SFTP 無法建立遠端目錄: " + pathStr, creationError);
                    }
                }
            }
        }
    }

    /**
     * 遞迴刪除遠端目錄下的所有檔案與子目錄，最後刪除該目錄本身
     *
     * @param sftp 已連線之 ChannelSftp
     * @param remoteDir 欲刪除的遠端目錄路徑
     * @throws SftpException 當檔案或目錄刪除失敗時拋出
     */
    private void deleteRemoteDirectoryRecursively(ChannelSftp sftp, String remoteDir) throws SftpException {
        @SuppressWarnings("unchecked")
        Vector<ChannelSftp.LsEntry> entries = sftp.ls(remoteDir);
        for (ChannelSftp.LsEntry entry : entries) {
            String name = entry.getFilename();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            String childPath = remoteDir + "/" + name;
            if (entry.getAttrs().isDir()) {
                deleteRemoteDirectoryRecursively(sftp, childPath);
            } else {
                sftp.rm(childPath);
            }
        }
        sftp.rmdir(remoteDir);
        log.info("[SFTP] 遞迴刪除遠端目錄成功: {}", remoteDir);
    }

    /**
     * 執行 SFTP 操作的範本方法，嚴格管理連線生命週期
     * <p>
     * 每次操作建立獨立的 JSch Session 與 ChannelSftp，在 finally 區塊中嚴格執行關閉，保證執行緒安全。
     * </p>
     *
     * @param config 傳輸伺服器連線配置
     * @param callback 傳輸業務回呼函式
     * @param <T> 回傳型別
     * @return 回呼執行結果
     * @throws TransmissionConnectionException 連線建立或認證異常時拋出
     * @throws FileTransmissionException 傳輸過程發生業務或 I/O 異常時拋出
     */
    private <T> T executeWithSftp(TransmissionServerConfig config, SftpCallback<T> callback) {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            JSch jsch = new JSch();

            // 若配置了 SSH 私鑰 (Private Key)，優先加入金鑰認證 (支援本機檔案路徑或純文字 PEM 內容)
            if (config.hasPrivateKey()) {
                String key = config.getPrivateKey();
                byte[] passphraseBytes = config.hasPassphrase()
                        ? config.getPassphrase().getBytes(StandardCharsets.UTF_8)
                        : null;

                File keyFile = new File(key);
                if (keyFile.exists() && keyFile.isFile()) {
                    jsch.addIdentity(keyFile.getAbsolutePath(), passphraseBytes);
                    log.info("[SFTP] 載入 SSH 私鑰檔案認證: path={}", keyFile.getAbsolutePath());
                } else {
                    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                    jsch.addIdentity("sftp-identity", keyBytes, null, passphraseBytes);
                    log.info("[SFTP] 載入 SSH 記憶體 PEM 私鑰認證");
                }
            }

            session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                session.setPassword(config.getPassword());
            }

            Properties sessionConfig = new Properties();
            sessionConfig.put(
                    "StrictHostKeyChecking",
                    config.isStrictHostKeyChecking() ? "yes" : "no"
            );
            session.setConfig(sessionConfig);
            session.setTimeout(config.getTimeoutMs());
            session.connect();

            Channel channel = session.openChannel("sftp");
            channel.connect(config.getTimeoutMs());
            sftp = (ChannelSftp) channel;

            return callback.doInSftp(sftp);
        } catch (JSchException e) {
            log.error("[SFTP] 連線或認證失敗: host={}, port={}, user={}, err={}",
                    config.getHost(), config.getPort(), config.getUsername(), e.getMessage());
            throw new TransmissionConnectionException("SFTP 連線失敗: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[SFTP] 執行操作時發生異常: {}", e.getMessage(), e);
            throw new FileTransmissionException("SFTP 操作執行失敗: " + e.getMessage(), e);
        } finally {
            if (sftp != null && sftp.isConnected()) {
                sftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    /**
     * SFTP 操作回呼函式介面 (Functional Interface)
     *
     * @param <T> 回傳型別
     */
    @FunctionalInterface
    private interface SftpCallback<T> {
        T doInSftp(ChannelSftp sftp) throws Exception;
    }
}
