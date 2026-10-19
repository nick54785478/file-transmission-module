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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
        // 比對傳輸協定列舉，本元件專責處理標準明文 FTP (File Transfer Protocol) 傳輸連線
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
        // 透過樣板模式統一管理 FTPClient 連線生命週期，確保 Socket 資源用畢自動關閉
        executeWithFtp(config, ftpClient -> {
            try {
                // 步驟 1：防禦性檢查並確保遠端目標目錄結構存在，若不存在則沿途徑逐層建立
                ensureRemoteDirectoryExists(ftpClient, remotePath.getValue());

                // 步驟 2：組合遠端目標檔案之完整路徑
                String targetFilePath = remotePath.getValue() + "/" + filename;

                File localFile = localFilePath.toFile();
                // 步驟 3：使用 Try-with-resources 宣告檔案輸入串流，確保串流必於傳輸結束後安全關閉，根除本機檔案鎖定問題
                try (InputStream inputStream = new FileInputStream(localFile)) {
                    // 步驟 4：透過 FTPClient.storeFile 傳送 STOR 指令以二進位串流寫入遠端伺服器
                    boolean success = ftpClient.storeFile(targetFilePath, inputStream);
                    if (!success) {
                        // 上傳失敗時取得 FTP 回應碼 (如 550 Permission Denied, 552 Exceeded Storage Allocation)
                        throw new FileTransmissionException("FTP 檔案存入失敗 (ReplyCode: " + ftpClient.getReplyCode() + ")");
                    }
                    log.info("[FTP] 上傳檔案成功: {} -> {}", localFilePath.getValue(), targetFilePath);
                }
                return null;
            } catch (IOException e) {
                // 步驟 5：將底層網路中斷或磁碟 I/O 異常轉譯為統一的傳輸業務例外
                log.error("[FTP] 上傳檔案發生異常: {}", e.getMessage(), e);
                throw new FileTransmissionException("FTP 上傳異常: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 將記憶體內的檔案位元組資料直接上傳至遠端 FTP 伺服器指定目錄 (零磁碟落盤)
     * <p>
     * <b>運作流程：</b>
     * <ol>
     *   <li>自動確認遠端目標目錄是否存在（若無則遞迴建立）。</li>
     *   <li>將記憶體中的 {@code byte[]} 封裝為 {@link ByteArrayInputStream}。</li>
     *   <li>透過 {@link FTPClient#storeFile(String, InputStream)} 以二進位串流直接傳輸寫入遠端，完全不需在本地產生任何實體暫存檔案。</li>
     * </ol>
     * </p>
     *
     * @param config 傳輸伺服器連線配置
     * @param content 記憶體檔案位元組內容
     * @param remotePath 遠端目標目錄路徑模型
     * @param filename 儲存於遠端之檔案名稱
     * @throws FileTransmissionException 當檔案存入失敗或 I/O 發生異常時拋出
     * @throws TransmissionConnectionException 當遠端連線建立或驗證失敗時拋出
     */
    @Override
    public void uploadFromMemory(TransmissionServerConfig config, byte[] content, RemoteFilePath remotePath, String filename) {
        Objects.requireNonNull(content, "記憶體檔案內容 content 不能為空");
        // 透過樣板模式統一管理 FTPClient 連線生命週期，確保 Socket 資源用畢自動關閉
        executeWithFtp(config, ftpClient -> {
            try {
                // 步驟 1：防禦性檢查並確保遠端目標目錄結構存在
                ensureRemoteDirectoryExists(ftpClient, remotePath.getValue());

                // 步驟 2：組合遠端目標檔案之完整路徑
                String targetFilePath = remotePath.getValue() + "/" + filename;

                // 步驟 3：透過 ByteArrayInputStream 直接讀取記憶體位元組，避免本地磁碟 I/O 開銷與暫存檔殘留
                try (InputStream inputStream = new ByteArrayInputStream(content)) {
                    // 步驟 4：透過 FTPClient.storeFile 傳送 STOR 指令寫入遠端伺服器
                    boolean success = ftpClient.storeFile(targetFilePath, inputStream);
                    if (!success) {
                        throw new FileTransmissionException("FTP 記憶體檔案存入失敗 (ReplyCode: " + ftpClient.getReplyCode() + ")");
                    }
                    log.info("[FTP] 記憶體檔案上傳成功: size={} bytes -> {}", content.length, targetFilePath);
                }
                return null;
            } catch (IOException e) {
                log.error("[FTP] 記憶體檔案上傳發生異常: {}", e.getMessage(), e);
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
        // 透過樣板方法調用 FTPClient 連線回呼
        executeWithFtp(config, ftpClient -> {
            try {
                // 步驟 1：檢查本機目標儲存檔案所在之父層目錄結構，若不存在則主動建立
                File localTarget = savedPath.toFile();
                File parentDir = localTarget.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    boolean created = parentDir.mkdirs();
                    if (!created) {
                        log.warn("[FTP] 建立本機父目錄失敗: {}", parentDir.getAbsolutePath());
                    }
                }

                // 步驟 2：使用 Try-with-resources 開啟本地檔案輸出串流
                try (OutputStream outputStream = new FileOutputStream(localTarget)) {
                    // 步驟 3：透過 FTPClient.retrieveFile 傳送 RETR 指令下載遠端檔案至本機輸出串流
                    boolean success = ftpClient.retrieveFile(remoteFilePath.getValue(), outputStream);
                    if (!success) {
                        // 下載失敗時獲取伺服器回應碼 (如 550 File not found)
                        throw new FileTransmissionException("FTP 檔案下載失敗 (ReplyCode: " + ftpClient.getReplyCode() + ")");
                    }
                    log.info("[FTP] 檔案下載成功: {} -> {}", remoteFilePath.getValue(), savedPath.getValue());
                }
                return null;
            } catch (IOException e) {
                // 步驟 4：捕捉並包裝 I/O 例外為業務異常
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
            // 依據同步傳輸方向列舉進行調度：
            if (direction == SyncDirection.LOCAL_TO_REMOTE) {
                // 方向 A：本機目錄 -> 遠端 FTP 目錄 (支援增量上傳與遠端孤兒檔案清理)
                return syncLocalToRemoteInternal(ftpClient, localDir.toFile(), remoteDir.getValue(), deleteRemoteExtras);
            } else {
                // 方向 B：遠端 FTP 目錄 -> 本機目錄 (比對時間戳記增量下載)
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
                // 步驟 1：向遠端發送 LIST 指令獲取目錄下的 FTPFile 項目清單
                FTPFile[] ftpFiles = ftpClient.listFiles(remotePath.getValue());
                if (ftpFiles != null) {
                    for (FTPFile file : ftpFiles) {
                        String name = file.getName();
                        // 步驟 2：過濾當前目錄 "." 與父層目錄 ".."，防止路徑解析遞迴死循環
                        if (".".equals(name) || "..".equals(name)) {
                            continue;
                        }
                        // 步驟 3：封裝為不可變的領域元資料物件 RemoteFileInfo
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
                // 發送 DELE 指令執行遠端檔案刪除
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
                // 深度優先搜尋 (DFS) 逐層清空目錄內部節點後再移除最外層目錄
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
            // 透過 executeWithFtp 驗證建立 TCP Socket、發送認證與登出全流程
            executeWithFtp(config, ftpClient -> true);
            return true;
        } catch (Exception e) {
            log.warn("[FTP] 測試連線失敗: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 檢查遠端 FTP 伺服器上是否存在指定檔案
     *
     * @param config 傳輸伺服器連線配置模型
     * @param remoteFilePath 欲檢查的遠端檔案路徑模型
     * @return 若檔案存在且為一般檔案回傳 {@code true}，否則回傳 {@code false}
     */
    @Override
    public boolean existsFile(TransmissionServerConfig config, RemoteFilePath remoteFilePath) {
        return executeWithFtp(config, ftpClient -> {
            try {
                FTPFile[] files = ftpClient.listFiles(remoteFilePath.getValue());
                if (files != null && files.length == 1) {
                    return files[0].isFile();
                }
                if (files != null && files.length > 1) {
                    for (FTPFile file : files) {
                        if (file.getName().equals(remoteFilePath.getFileName()) && file.isFile()) {
                            return true;
                        }
                    }
                }
                return false;
            } catch (IOException e) {
                log.warn("[FTP] 檢查遠端檔案是否存在異常: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * 移動或重新命名遠端 FTP 伺服器上的檔案 (可用於歷史版本備份與歸檔)
     *
     * @param config 傳輸伺服器連線配置模型
     * @param sourcePath 來源檔案路徑模型
     * @param targetPath 目標檔案路徑模型
     * @throws FileTransmissionException 當檔案移動或重新命名失敗時拋出
     */
    @Override
    public void moveFile(TransmissionServerConfig config, RemoteFilePath sourcePath, RemoteFilePath targetPath) {
        executeWithFtp(config, ftpClient -> {
            try {
                ensureRemoteDirectoryExists(ftpClient, targetPath.getParent().getValue());
                boolean success = ftpClient.rename(sourcePath.getValue(), targetPath.getValue());
                if (!success) {
                    throw new FileTransmissionException("FTP 檔案移動/重新命名失敗 (ReplyCode: " + ftpClient.getReplyCode() + ")");
                }
                log.info("[FTP] 遠端檔案移動/重命名成功: {} -> {}", sourcePath.getValue(), targetPath.getValue());
                return null;
            } catch (IOException e) {
                log.error("[FTP] 移動/重新命名檔案發生異常: {}", e.getMessage(), e);
                throw new FileTransmissionException("FTP 檔案移動/重命名異常: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 取得遠端 FTP 伺服器上指定檔案之位元組大小
     *
     * @param config 傳輸伺服器連線配置模型
     * @param remoteFilePath 欲查詢的遠端檔案路徑模型
     * @return 檔案位元組大小，若檔案不存在或為目錄則回傳 -1L
     */
    @Override
    public long getRemoteFileSize(TransmissionServerConfig config, RemoteFilePath remoteFilePath) {
        return executeWithFtp(config, ftpClient -> {
            try {
                FTPFile[] files = ftpClient.listFiles(remoteFilePath.getValue());
                if (files != null && files.length == 1 && files[0].isFile()) {
                    return files[0].getSize();
                }
                if (files != null && files.length > 1) {
                    for (FTPFile file : files) {
                        if (file.getName().equals(remoteFilePath.getFileName()) && file.isFile()) {
                            return file.getSize();
                        }
                    }
                }
                return -1L;
            } catch (IOException e) {
                log.warn("[FTP] 取得遠端檔案大小異常: {}", e.getMessage());
                return -1L;
            }
        });
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
        // 步驟 1：防禦性驗證本機來源路徑是否存在且為有效目錄
        if (!localDirectory.exists() || !localDirectory.isDirectory()) {
            throw new IllegalArgumentException("本機路徑非有效目錄: " + localDirectory.getAbsolutePath());
        }

        // 步驟 2：確保遠端目標目錄結構存在，若不存在則沿路徑逐層遞迴建立
        ensureRemoteDirectoryExists(ftpClient, remoteDirPath);

        // 步驟 3：列舉本機目錄下的所有子檔案與子目錄清單，若為空目錄直接結束
        File[] localFiles = localDirectory.listFiles();
        if (localFiles == null) {
            return 0;
        }

        // 步驟 4：查詢遠端目錄現存檔案清單，建立檔名集合快取作為增量比對基準
        FTPFile[] remoteFiles = ftpClient.listFiles(remoteDirPath);
        Set<String> remoteFileNames = new HashSet<>();
        if (remoteFiles != null) {
            for (FTPFile rf : remoteFiles) {
                remoteFileNames.add(rf.getName());
            }
        }

        int count = 0;
        // 步驟 5：走訪本機檔案項目，進行遞迴同步或檔案上傳
        for (File localFile : localFiles) {
            String name = localFile.getName();
            if (localFile.isDirectory()) {
                // 子目錄：組合遠端對應子目錄路徑，遞迴深度同步
                String subRemote = remoteDirPath + "/" + name;
                count += syncLocalToRemoteInternal(ftpClient, localFile, subRemote, deleteRemoteExtras);
            } else {
                // 一般檔案：增量檢查，若遠端尚未存在同名檔案才執行上傳
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

        // 步驟 6：依配置比對並清理遠端多餘孤兒檔案 (本機已不存在但遠端仍殘留的檔案/目錄)
        if (deleteRemoteExtras && remoteFiles != null) {
            Set<String> localNames = new HashSet<>(Arrays.asList(localDirectory.list()));
            for (FTPFile remoteFile : remoteFiles) {
                String name = remoteFile.getName();
                // 略過特殊目錄符號 "." 與 ".."
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }
                // 若遠端檔案名稱不存在於本機目錄清單中，予以清理
                if (!localNames.contains(name)) {
                    String remotePath = remoteDirPath + "/" + name;
                    if (remoteFile.isDirectory()) {
                        // 遞迴清空並刪除遠端多餘子目錄
                        deleteRemoteDirectoryRecursively(ftpClient, remotePath);
                    } else {
                        // 刪除遠端多餘單一檔案
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
        // 步驟 1：確保本機目標目錄結構存在，若不存在則主動建立父子層目錄
        if (!localDirectory.exists()) {
            boolean created = localDirectory.mkdirs();
            if (!created) {
                log.warn("[FTP] 建立本機目錄失敗: {}", localDirectory.getAbsolutePath());
            }
        }

        int count = 0;
        // 步驟 2：查詢遠端來源目錄下的所有檔案與子目錄清單
        FTPFile[] remoteFiles = ftpClient.listFiles(remoteDirPath);
        if (remoteFiles == null) {
            return 0;
        }

        // 步驟 3：走訪遠端各項目，進行增量下載或遞迴目錄處理
        for (FTPFile remoteFile : remoteFiles) {
            String name = remoteFile.getName();
            // 略過 "." 與 ".."
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }

            String subRemotePath = remoteDirPath + "/" + name;
            File subLocalFile = new File(localDirectory, name);

            if (remoteFile.isDirectory()) {
                // 遇到目錄：遞迴走訪同步子目錄
                count += syncRemoteToLocalInternal(ftpClient, subRemotePath, subLocalFile);
            } else {
                // 遇到檔案：比對時間戳記進行增量下載
                long remoteTime = (remoteFile.getTimestamp() != null) ? remoteFile.getTimestamp().getTimeInMillis() : 0L;
                // 若本機檔案不存在，或本機檔案修改時間早於遠端檔案 (遠端有新版本)，則執行下載
                if (!subLocalFile.exists() || subLocalFile.lastModified() < remoteTime) {
                    try (OutputStream os = new FileOutputStream(subLocalFile)) {
                        ftpClient.retrieveFile(subRemotePath, os);
                        // 下載完成後將本機檔案修改時間同步為遠端檔案時間，確保後續增量比對精準
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
        // 步驟 1：依據正斜線 "/" 切割路徑元素，準備逐層向下檢查與建立
        String[] pathElements = remotePath.split("/");
        StringBuilder currentPath = new StringBuilder();

        for (String folder : pathElements) {
            if (folder.isEmpty()) {
                continue;
            }
            currentPath.append("/").append(folder);
            String directory = currentPath.toString();

            // 步驟 2：嘗試切換工作目錄至該層級 (CWD 指令)
            if (!ftpClient.changeWorkingDirectory(directory)) {
                // 切換失敗代表該層目錄尚未建立，發送 MKD 指令建立目錄
                boolean created = ftpClient.makeDirectory(directory);
                if (!created) {
                    throw new RemoteDirectoryCreationException("FTP 無法建立遠端目錄: " + directory);
                }
                // 建立後切換進入，確保後續下層目錄可在此基礎上順利銜接
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
        // 步驟 1：列舉遠端目標目錄下的所有檔案與子項目
        FTPFile[] remoteFiles = ftpClient.listFiles(remoteDir);
        if (remoteFiles != null) {
            for (FTPFile remoteFile : remoteFiles) {
                String name = remoteFile.getName();
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }
                String subPath = remoteDir + "/" + name;
                // 步驟 2：深度優先走訪 (DFS)
                if (remoteFile.isDirectory()) {
                    // 若為子目錄，先遞迴清空該子目錄內部所有內容
                    deleteRemoteDirectoryRecursively(ftpClient, subPath);
                } else {
                    // 若為檔案，直接發送 DELE 指令刪除
                    ftpClient.deleteFile(subPath);
                }
            }
        }
        // 步驟 3：目錄內部所有子項目全數清空後，發送 RMD 指令移除空目錄本身
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
        // 步驟 1：實例化標準 FTPClient
        FTPClient ftpClient = new FTPClient();

        // 步驟 2：配置編碼與逾時參數
        // 強制控制通道使用 UTF-8 編碼，防止中文檔名或特殊路徑在指令解析時出現亂碼
        ftpClient.setControlEncoding(StandardCharsets.UTF_8.name());

        // 設定 TCP Socket 連線逾時 (毫秒)
        ftpClient.setConnectTimeout(config.getTimeoutMs());

        // 設定資料通道 (Data Channel) 讀取與傳輸 Socket 逾時 (使用 Duration 避免過時方法警告)
        ftpClient.setDataTimeout(Duration.ofMillis(config.getTimeoutMs()));

        // 設定控制通道 (Control Channel) 指令與回應接收之預設逾時 (毫秒)
        ftpClient.setDefaultTimeout(config.getTimeoutMs());

        // 將底層 I/O 緩衝區擴增為 1MB (1024 * 1024 bytes)，提高檔案傳輸吞吐量
        ftpClient.setBufferSize(1024 * 1024);

        // 步驟 3：向遠端伺服器發起 TCP 連線建立
        ftpClient.connect(config.getHost(), config.getPort());

        // 步驟 4：校驗伺服器初始回應碼 (ReplyCode)
        // 2xx 代表 Positive Completion (如 220 Service ready for new user)，非 2xx 則判定為連線被拒
        int replyCode = ftpClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(replyCode)) {
            ftpClient.disconnect();
            throw new TransmissionConnectionException("FTP 伺服器拒絕連線 (ReplyCode: " + replyCode + ")");
        }

        // 步驟 5：傳送帳號密碼執行身分驗證 (LOGIN)
        boolean loginSuccess = ftpClient.login(config.getUsername(), config.getPassword());
        if (!loginSuccess) {
            // 驗證失敗立即中斷 Socket，防止殘留未認證連線
            ftpClient.disconnect();
            throw new TransmissionConnectionException("FTP 登入失敗: 帳號或密碼錯誤 (user=" + config.getUsername() + ")");
        }

        // 步驟 6：依據配置切換傳輸通道模式 (被動模式 Passive vs 主動模式 Active)
        // 被動模式由伺服器開啟資料通道通訊埠讓客戶端連入，為穿透防火牆與 NAT 之業界標準建議模式
        if (config.isPassiveMode()) {
            ftpClient.enterLocalPassiveMode();
        } else {
            ftpClient.enterLocalActiveMode();
        }

        // 步驟 7：強制設定為二進位檔案傳輸模式 (Binary Mode)
        // 避免預設的 ASCII 模式因換行符號 (\r\n) 轉換而破壞非純文字檔案
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

        log.info("[FTP] 連線與身分驗證成功: {}:{} (passiveMode={})",
                config.getHost(), config.getPort(), config.isPassiveMode());

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
            // 步驟 1：建立連線、身分驗證與通道設定
            ftpClient = createAndConnectClient(config);

            // 步驟 2：執行業務操作回呼
            return callback.doInFtp(ftpClient);
        } catch (IOException e) {
            // 步驟 3：例外轉譯 - 將底層 Socket 網路中斷轉為連線例外
            log.error("[FTP] 連線或傳輸發生異常: {}", e.getMessage(), e);
            throw new TransmissionConnectionException("FTP 操作失敗: " + e.getMessage(), e);
        } catch (Exception e) {
            // 步驟 4：例外轉譯 - 將傳輸業務邏輯例外轉為統一傳輸業務例外
            log.error("[FTP] 執行操作時發生異常: {}", e.getMessage(), e);
            throw new FileTransmissionException("FTP 操作執行失敗: " + e.getMessage(), e);
        } finally {
            // 步驟 5：資源釋放保證 - 於 finally 區塊強制登出並關閉 Socket，杜絕連線資源洩漏
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
