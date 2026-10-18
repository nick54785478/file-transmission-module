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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Vector;

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
        // 比對傳輸協定列舉，本元件專責處理基於 SSH 加密通道之 SFTP (SSH File Transfer Protocol) 傳輸連線
        return TransmissionProtocol.SFTP == protocol;
    }

    /**
     * 上傳單一檔案至遠端 SFTP 伺服器指定目錄
     * <p>
     * 自動確認並建立遠端目標目錄，切換目錄後透過串流完成寫入上傳。
     * </p>
     *
     * @param config        傳輸伺服器連線配置
     * @param localFilePath 本機來源檔案路徑模型
     * @param remotePath    遠端目標目錄路徑模型
     * @param filename      儲存於遠端之檔案名稱
     * @throws FileTransmissionException       當上傳過程發生 SFTP 協定錯誤或本機 I/O 異常時拋出
     * @throws TransmissionConnectionException 當 SSH 連線建立或認證失敗時拋出
     */
    @Override
    public void upload(TransmissionServerConfig config, LocalFilePath localFilePath, RemoteFilePath remotePath, String filename) {
        // 透過樣板方法建立獨立隔離的 SSH Session 與 ChannelSftp，確保執行緒安全並保證連線自動釋放
        executeWithSftp(config, sftp -> {
            try {
                // 步驟 1：防禦性檢查並確保遠端目標目錄結構存在，若不存在則沿途徑逐層建立
                ensureRemoteDirectoryExists(sftp, remotePath.getValue());

                // 步驟 2：切換至遠端目標工作目錄 (CD 指令)
                sftp.cd(remotePath.getValue());

                File localFile = localFilePath.toFile();
                // 步驟 3：使用 Try-with-resources 開啟本機檔案輸入串流，確保串流必於傳輸完畢後安全關閉
                try (InputStream inputStream = new FileInputStream(localFile)) {
                    // 步驟 4：透過 ChannelSftp.put 以串流方式寫入遠端伺服器 (檔名已於目標目錄下)
                    sftp.put(inputStream, filename);
                    log.info("[SFTP] 檔案上傳成功: {} -> {}/{}", localFilePath.getValue(), remotePath.getValue(), filename);
                }
                return null;
            } catch (SftpException | IOException e) {
                // 步驟 5：將底層 SFTP 協定異常 (如權限不足、配額超額) 或磁碟 I/O 異常轉譯為領域業務例外
                log.error("[SFTP] 檔案上傳發生異常: {}", e.getMessage(), e);
                throw new FileTransmissionException("SFTP 上傳失敗: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 將記憶體內的檔案位元組資料直接上傳至遠端 SFTP 伺服器指定目錄 (零磁碟落盤)
     * <p>
     * <b>運作流程：</b>
     * <ol>
     *   <li>自動確認遠端目標目錄是否存在（若無則遞迴建立）並切換進入該目錄。</li>
     *   <li>將記憶體中的 {@code byte[]} 封裝為 {@link ByteArrayInputStream}。</li>
     *   <li>透過 {@link ChannelSftp#put(InputStream, String)} 以 SSH 加密串流直接傳輸寫入遠端，完全不需在本地產生任何實體暫存檔案。</li>
     * </ol>
     * </p>
     *
     * @param config        傳輸伺服器連線配置
     * @param content       記憶體檔案位元組內容
     * @param remotePath    遠端目標目錄路徑模型
     * @param filename      儲存於遠端之檔案名稱
     * @throws FileTransmissionException       當檔案上傳失敗或 I/O 發生異常時拋出
     * @throws TransmissionConnectionException 當 SSH 連線建立或認證失敗時拋出
     */
    @Override
    public void uploadFromMemory(TransmissionServerConfig config, byte[] content, RemoteFilePath remotePath, String filename) {
        Objects.requireNonNull(content, "記憶體檔案內容 content 不能為空");
        // 透過樣板方法建立獨立隔離的 SSH Session 與 ChannelSftp，確保執行緒安全並保證連線自動釋放
        executeWithSftp(config, sftp -> {
            try {
                // 步驟 1：防禦性檢查並確保遠端目標目錄結構存在，若不存在則沿途徑逐層建立
                ensureRemoteDirectoryExists(sftp, remotePath.getValue());

                // 步驟 2：切換至遠端目標工作目錄 (CD 指令)
                sftp.cd(remotePath.getValue());

                // 步驟 3：透過 ByteArrayInputStream 直接讀取記憶體位元組，推送到 SSH 加密通道
                try (InputStream inputStream = new ByteArrayInputStream(content)) {
                    // 步驟 4：透過 ChannelSftp.put 以串流方式寫入遠端伺服器 (檔名已於目標目錄下)
                    sftp.put(inputStream, filename);
                    log.info("[SFTP] 記憶體檔案上傳成功: size={} bytes -> {}/{}",
                            content.length, remotePath.getValue(), filename);
                }
                return null;
            } catch (SftpException | IOException e) {
                log.error("[SFTP] 記憶體檔案上傳發生異常: {}", e.getMessage(), e);
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
     * @param config         傳輸伺服器連線配置
     * @param remoteFilePath 遠端來源檔案之完整路徑模型
     * @param savedPath      本機儲存之目標檔案路徑模型
     * @throws FileTransmissionException       當檔案下載失敗或寫入本機異常時拋出
     * @throws TransmissionConnectionException 當 SSH 連線建立失敗時拋出
     */
    @Override
    public void download(TransmissionServerConfig config, RemoteFilePath remoteFilePath, LocalFilePath savedPath) {
        // 透過樣板方法調用 SFTP 回呼邏輯
        executeWithSftp(config, sftp -> {
            try {
                // 步驟 1：檢查本機目標儲存檔案所在之父層目錄結構，若不存在則主動建立
                File localTarget = savedPath.toFile();
                File parentDir = localTarget.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    boolean created = parentDir.mkdirs();
                    if (!created) {
                        log.warn("[SFTP] 建立本機目錄失敗: {}", parentDir.getAbsolutePath());
                    }
                }

                // 步驟 2：使用 Try-with-resources 開啟本地檔案輸出串流
                try (OutputStream outputStream = new FileOutputStream(localTarget)) {
                    // 步驟 3：透過 ChannelSftp.get 自遠端以二進位串流下載並寫入本機
                    sftp.get(remoteFilePath.getValue(), outputStream);
                    log.info("[SFTP] 檔案下載成功: {} -> {}", remoteFilePath.getValue(), savedPath.getValue());
                }
                return null;
            } catch (SftpException | IOException e) {
                // 步驟 4：捕捉並包裝底層協定與 I/O 異常
                log.error("[SFTP] 檔案下載發生異常: {}", e.getMessage(), e);
                throw new FileTransmissionException("SFTP 下載失敗: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 執行本機目錄與遠端 SFTP 目錄的雙向同步作業
     *
     * @param config             傳輸伺服器連線配置
     * @param localDir           本機目錄路徑模型
     * @param remoteDir          遠端目錄路徑模型
     * @param direction          同步傳輸方向 (本機至遠端 或 遠端至本機)
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
            // 依據傳輸方向列舉進行調度：
            if (direction == SyncDirection.LOCAL_TO_REMOTE) {
                // 方向 A：本機目錄 -> 遠端 SFTP 目錄 (智慧時間戳記增量比對上傳)
                return syncLocalToRemoteInternal(sftp, localDir.toFile(), remoteDir.getValue());
            } else {
                // 方向 B：遠端 SFTP 目錄 -> 本機目錄 (智慧時間戳記增量比對下載)
                return syncRemoteToLocalInternal(sftp, remoteDir.getValue(), localDir.toFile());
            }
        });
    }

    /**
     * 查詢遠端 SFTP 目錄下的檔案與子目錄清單元資料
     *
     * @param config     傳輸伺服器連線配置
     * @param remotePath 欲查詢的遠端目錄路徑模型
     * @return 遠端檔案中繼資料清單，已排除 "." 與 ".."
     * @throws FileTransmissionException 當查詢目錄發生錯誤時拋出
     */
    @Override
    public List<RemoteFileInfo> listFiles(TransmissionServerConfig config, RemoteFilePath remotePath) {
        return executeWithSftp(config, sftp -> {
            List<RemoteFileInfo> result = new ArrayList<>();
            try {
                // 步驟 1：向 SFTP 伺服器發送 LS 指令獲取目錄下的 LsEntry 項目向量清單
                Vector<ChannelSftp.LsEntry> entries = sftp.ls(remotePath.getValue());
                for (ChannelSftp.LsEntry entry : entries) {
                    String name = entry.getFilename();
                    // 步驟 2：過濾當前目錄 "." 與父層目錄 ".."，防止路徑解析死循環
                    if (".".equals(name) || "..".equals(name)) {
                        continue;
                    }
                    // 步驟 3：自 LsEntry 提取 SftpATTRS 並轉換為毫秒時間戳記 (mtime 原始為秒)
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
     * @param config         傳輸伺服器連線配置
     * @param remoteFilePath 欲刪除的遠端檔案路徑模型
     * @return 若刪除成功回傳 {@code true}，檔案不存在或失敗回傳 {@code false}
     */
    @Override
    public boolean deleteFile(TransmissionServerConfig config, RemoteFilePath remoteFilePath) {
        return executeWithSftp(config, sftp -> {
            try {
                // 發送 RM 指令執行遠端檔案刪除
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
     * @param config              傳輸伺服器連線配置
     * @param remoteDirectoryPath 欲刪除的遠端目錄路徑模型
     * @return 若遞迴刪除成功回傳 {@code true}，否則回傳 {@code false}
     */
    @Override
    public boolean deleteDirectory(TransmissionServerConfig config, RemoteFilePath remoteDirectoryPath) {
        return executeWithSftp(config, sftp -> {
            try {
                // 深度優先搜尋 (DFS) 逐層清空目錄內部節點後再移除最外層目錄
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
            // 透過 executeWithSftp 完整驗證 SSH 連線建立、金鑰/密碼認證及開闢 sftp 通道全流程
            executeWithSftp(config, sftp -> true);
            return true;
        } catch (Exception e) {
            log.warn("[SFTP] 測試連線失敗: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 檢查遠端 SFTP 伺服器上是否存在指定檔案
     *
     * @param config 傳輸伺服器連線配置
     * @param remoteFilePath 欲檢查的遠端檔案路徑模型
     * @return 若檔案存在且非目錄回傳 true，否則回傳 false
     */
    @Override
    public boolean existsFile(TransmissionServerConfig config, RemoteFilePath remoteFilePath) {
        return executeWithSftp(config, sftp -> {
            try {
                SftpATTRS attrs = sftp.stat(remoteFilePath.getValue());
                return attrs != null && !attrs.isDir();
            } catch (SftpException e) {
                return false;
            }
        });
    }

    /**
     * 移動或重新命名遠端 SFTP 伺服器上的檔案 (可用於歷史版本備份與歸檔)
     *
     * @param config 傳輸伺服器連線配置
     * @param sourcePath 來源檔案路徑
     * @param targetPath 目標檔案路徑
     */
    @Override
    public void moveFile(TransmissionServerConfig config, RemoteFilePath sourcePath, RemoteFilePath targetPath) {
        executeWithSftp(config, sftp -> {
            try {
                ensureRemoteDirectoryExists(sftp, targetPath.getParent().getValue());
                sftp.rename(sourcePath.getValue(), targetPath.getValue());
                log.info("[SFTP] 遠端檔案移動/重命名成功: {} -> {}", sourcePath.getValue(), targetPath.getValue());
                return null;
            } catch (SftpException e) {
                throw new FileTransmissionException("SFTP 檔案移動/重新命名失敗: " + e.getMessage(), e);
            }
        });
    }

    // ================================= 內部輔助與重構邏輯 =================================

    /**
     * 內部遞迴同步實作：從本機目錄同步至遠端 SFTP 目錄
     *
     * @param sftp           已連線之 ChannelSftp
     * @param localDirectory 本機來源目錄
     * @param remoteDirPath  遠端目標目錄完整路徑
     * @return 傳輸成功檔案總數
     */
    private int syncLocalToRemoteInternal(ChannelSftp sftp, File localDirectory, String remoteDirPath) {
        // 步驟 1：防禦性驗證本機來源路徑是否存在且為有效目錄
        if (!localDirectory.exists() || !localDirectory.isDirectory()) {
            throw new IllegalArgumentException("本機路徑非有效目錄: " + localDirectory.getAbsolutePath());
        }

        int count = 0;
        try {
            // 步驟 2：確保遠端目標目錄結構存在，若不存在則沿路徑逐層遞迴建立
            ensureRemoteDirectoryExists(sftp, remoteDirPath);

            // 步驟 3：列舉本機目錄下的所有檔案與子目錄清單
            File[] localFiles = localDirectory.listFiles();
            if (localFiles == null) {
                return 0;
            }

            // 步驟 4：發送 LS 指令快取遠端目標路徑下的現有項目清單，作為增量比對基準
            Vector<ChannelSftp.LsEntry> remoteEntries = sftp.ls(remoteDirPath);
            for (File file : localFiles) {
                if (file.isFile()) {
                    boolean needsUpload = true;
                    // 步驟 5-A：比對遠端檔案之最後修改時間 (mtime) 進行增量智慧檢查
                    for (ChannelSftp.LsEntry entry : remoteEntries) {
                        if (entry.getFilename().equals(file.getName())) {
                            // 若遠端檔案最後修改時間大於等於本機檔案 (遠端已是最新或相同)，則跳過重複上傳
                            if (file.lastModified() <= entry.getAttrs().getMTime() * 1000L) {
                                needsUpload = false;
                            }
                            break;
                        }
                    }

                    // 步驟 5-B：若判定需要上傳 (遠端無此檔或本機版本更新)，以串流方式上傳寫入
                    if (needsUpload) {
                        try (InputStream is = new FileInputStream(file)) {
                            sftp.cd(remoteDirPath);
                            sftp.put(is, file.getName());
                            count++;
                            log.info("[SFTP] 同步檔案至遠端: {} -> {}", file.getAbsolutePath(), remoteDirPath);
                        }
                    }
                } else if (file.isDirectory()) {
                    // 步驟 5-C：若為子目錄，組合遠端對應子目錄路徑並遞迴同步
                    String subRemoteDir = remoteDirPath + "/" + file.getName();
                    count += syncLocalToRemoteInternal(sftp, file, subRemoteDir);
                }
            }
        } catch (SftpException | IOException e) {
            // 步驟 6：將傳輸或 I/O 異常轉譯為領域業務例外
            throw new FileTransmissionException("SFTP 本機同步至遠端異常: " + e.getMessage(), e);
        }
        return count;
    }

    /**
     * 內部遞迴同步實作：從遠端 SFTP 目錄同步至本機目錄
     *
     * @param sftp           已連線之 ChannelSftp
     * @param remoteDirPath  遠端來源目錄完整路徑
     * @param localDirectory 本機目標目錄
     * @return 傳輸成功檔案總數
     */
    private int syncRemoteToLocalInternal(ChannelSftp sftp, String remoteDirPath, File localDirectory) {
        // 步驟 1：確保本機目標目錄結構存在，若不存在則主動建立父子層目錄
        if (!localDirectory.exists()) {
            boolean created = localDirectory.mkdirs();
            if (!created) {
                log.warn("[SFTP] 建立本機目錄失敗: {}", localDirectory.getAbsolutePath());
            }
        }

        int count = 0;
        try {
            // 步驟 2：查詢遠端來源目錄下的所有檔案與子目錄清單
            Vector<ChannelSftp.LsEntry> remoteEntries = sftp.ls(remoteDirPath);
            for (ChannelSftp.LsEntry entry : remoteEntries) {
                String entryName = entry.getFilename();
                // 略過 "." 與 ".." 特殊目錄指標
                if (".".equals(entryName) || "..".equals(entryName)) {
                    continue;
                }

                String subRemotePath = remoteDirPath + "/" + entryName;
                File subLocalFile = new File(localDirectory, entryName);

                if (entry.getAttrs().isDir()) {
                    // 步驟 3-A：遇到目錄：遞迴走訪同步子目錄
                    count += syncRemoteToLocalInternal(sftp, subRemotePath, subLocalFile);
                } else {
                    // 步驟 3-B：遇到檔案：比對時間戳記進行增量下載
                    long remoteTime = entry.getAttrs().getMTime() * 1000L;
                    // 若本機檔案不存在或本機修改時間早於遠端 (遠端有更新版本)，則執行下載
                    if (!subLocalFile.exists() || subLocalFile.lastModified() < remoteTime) {
                        try (OutputStream os = new FileOutputStream(subLocalFile)) {
                            sftp.get(subRemotePath, os);
                            // 下載完成後將本機檔案修改時間同步為遠端檔案時間，確保後續增量比對精準
                            subLocalFile.setLastModified(remoteTime);
                            count++;
                            log.info("[SFTP] 同步檔案至本機: {} -> {}", subRemotePath, subLocalFile.getAbsolutePath());
                        }
                    }
                }
            }
        } catch (SftpException | IOException e) {
            // 步驟 4：捕捉並包裝異常為業務例外
            throw new FileTransmissionException("SFTP 遠端同步至本機異常: " + e.getMessage(), e);
        }
        return count;
    }

    /**
     * 確保遠端目標目錄存在，若不存在則沿路徑逐層遞迴建立
     *
     * @param sftp       已連線之 ChannelSftp
     * @param remotePath 欲檢查與建立的遠端目錄路徑
     * @throws SftpException 當遠端目錄操作發生協定異常時拋出
     */
    private void ensureRemoteDirectoryExists(ChannelSftp sftp, String remotePath) throws SftpException {
        // 步驟 1：效能最佳化前置探測 - 嘗試直接切換至目標路徑，若目錄已存在則直接返回
        try {
            sftp.cd(remotePath);
        } catch (SftpException e) {
            // 步驟 2：切換失敗代表路徑中部分或全部目錄尚未建立，依據正斜線 "/" 切割各層路徑元素
            String[] directories = remotePath.split("/");
            StringBuilder currentPath = new StringBuilder();
            for (String dir : directories) {
                if (dir.isEmpty()) {
                    continue;
                }
                currentPath.append("/").append(dir);
                String pathStr = currentPath.toString();
                // 步驟 3：由根目錄向下逐層嘗試切換，若不存在則建立該層目錄
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
     * @param sftp      已連線之 ChannelSftp
     * @param remoteDir 欲刪除的遠端目錄路徑
     * @throws SftpException 當檔案或目錄刪除失敗時拋出
     */
    private void deleteRemoteDirectoryRecursively(ChannelSftp sftp, String remoteDir) throws SftpException {
        // 步驟 1：列舉遠端目標目錄下的所有檔案與子項目
        Vector<ChannelSftp.LsEntry> entries = sftp.ls(remoteDir);
        for (ChannelSftp.LsEntry entry : entries) {
            String name = entry.getFilename();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            String childPath = remoteDir + "/" + name;
            // 步驟 2：深度優先走訪 (DFS)
            if (entry.getAttrs().isDir()) {
                // 若為子目錄，先遞迴清空該子目錄內部所有內容
                deleteRemoteDirectoryRecursively(sftp, childPath);
            } else {
                // 若為檔案，直接發送 RM 指令刪除
                sftp.rm(childPath);
            }
        }
        // 步驟 3：目錄內部所有子項目全數清空後，發送 RMDIR 指令移除空目錄本身
        sftp.rmdir(remoteDir);
        log.info("[SFTP] 遞迴刪除遠端目錄成功: {}", remoteDir);
    }

    /**
     * 執行 SFTP 操作的範本方法，嚴格管理連線生命週期
     * <p>
     * 每次操作建立獨立的 JSch Session 與 ChannelSftp，在 finally 區塊中嚴格執行關閉，保證執行緒安全。
     * </p>
     *
     * @param config   傳輸伺服器連線配置
     * @param callback 傳輸業務回呼函式
     * @param <T>      回傳型別
     * @return 回呼執行結果
     * @throws TransmissionConnectionException 連線建立或認證異常時拋出
     * @throws FileTransmissionException       傳輸過程發生業務或 I/O 異常時拋出
     */
    private <T> T executeWithSftp(TransmissionServerConfig config, SftpCallback<T> callback) {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            // 步驟 1：實例化 JSch 引擎
            JSch jsch = new JSch();

            // 步驟 2：SSH 金鑰認證配置 (Publickey Authentication)
            // 若配置了 SSH 私鑰 (Private Key)，優先加入金鑰認證 (支援本機檔案路徑或純文字 PEM 內容)
            if (config.hasPrivateKey()) {
                String key = config.getPrivateKey();
                byte[] passphraseBytes = config.hasPassphrase()
                        ? config.getPassphrase().getBytes(StandardCharsets.UTF_8)
                        : null;

                File keyFile = new File(key);
                if (keyFile.exists() && keyFile.isFile()) {
                    // 方式 A：本機實體檔案私鑰載入
                    jsch.addIdentity(keyFile.getAbsolutePath(), passphraseBytes);
                    log.info("[SFTP] 載入 SSH 私鑰檔案認證: path={}", keyFile.getAbsolutePath());
                } else {
                    // 方式 B：記憶體純文字 PEM 私鑰位元組載入 (適用於資料庫/環境變數動態傳入)
                    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                    jsch.addIdentity("sftp-identity", keyBytes, null, passphraseBytes);
                    log.info("[SFTP] 載入 SSH 記憶體 PEM 私鑰認證");
                }
            }

            // 步驟 3：建立 SSH Session，若提供密碼則配置密碼認證 (Password Authentication)
            session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                session.setPassword(config.getPassword());
            }

            // 步驟 4：設定 SSH 連線參數 (StrictHostKeyChecking)
            // - "no"：跳過 HostKey 檢查，適用於測試環境或動態 IP 伺服器
            // - "yes"：生產環境嚴格驗證 known_hosts，防禦中間人攻擊 (MITM)
            Properties sessionConfig = new Properties();
            sessionConfig.put(
                    "StrictHostKeyChecking",
                    config.isStrictHostKeyChecking() ? "yes" : "no"
            );
            session.setConfig(sessionConfig);

            // 步驟 5：配置連線逾時 (毫秒) 並發起 TCP SSH 連線握手
            session.setTimeout(config.getTimeoutMs());
            session.connect();

            // 步驟 6：在 SSH 通道上開闢 SFTP 子通道 (ChannelSftp)
            Channel channel = session.openChannel("sftp");
            channel.connect(config.getTimeoutMs());
            sftp = (ChannelSftp) channel;

            // 步驟 7：執行傳輸業務回呼
            return callback.doInSftp(sftp);
        } catch (JSchException e) {
            // 步驟 8：例外轉譯 - 將 JSch 底層連線或認證失敗轉譯為連線例外
            log.error("[SFTP] 連線或認證失敗: host={}, port={}, user={}, err={}",
                    config.getHost(), config.getPort(), config.getUsername(), e.getMessage());
            throw new TransmissionConnectionException("SFTP 連線失敗: " + e.getMessage(), e);
        } catch (Exception e) {
            // 步驟 9：例外轉譯 - 將傳輸業務邏輯例外轉譯為統一業務例外
            log.error("[SFTP] 執行操作時發生異常: {}", e.getMessage(), e);
            throw new FileTransmissionException("SFTP 操作執行失敗: " + e.getMessage(), e);
        } finally {
            // 步驟 10：資源釋放保證 - 於 finally 區塊依序關閉 Channel 與 Session，杜絕連線數洩漏
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
