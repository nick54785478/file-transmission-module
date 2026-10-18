package com.example.demo.application.port.out;

import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFileInfo;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import java.util.List;

/**
 * 檔案傳輸閘道輸出埠介面 (FileTransmissionGatewayPort - Outbound Port)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>out : ... + Port</code> (AGENTS.md 3-8)。</li>
 *   <li>六角形架構 Outbound Port，作為應用層與外部遠端伺服器（FTP, FTPS, SFTP）通訊的唯一標準出入口閘道合約。</li>
 *   <li>專職負責跨網路技術協定的實體傳輸與遠端檔案系統操作（上傳、下載、目錄同步、列表查詢、檔案刪除與連線健康檢查），
 *       本身為無狀態（Stateless）技術合約，不介入傳輸工作階段之業務狀態維護。</li>
 *   <li><b>純淨規範守則：</b> 本介面嚴禁出現任何底層技術類別（如 Apache Commons Net FTPClient、JSch Session / ChannelSftp 等），
 *       僅可使用純領域實質物件 (Value Objects) 與 Java 標準集合。</li>
 * </ul>
 * </p>
 */
public interface FileTransmissionGatewayPort {

    /**
     * 上傳本機檔案至遠端伺服器指定目錄
     *
     * @param config 伺服器連線配置
     * @param localFilePath 本機來源檔案路徑
     * @param remotePath 遠端目標目錄
     * @param filename 遠端目標檔案名稱
     */
    void upload(TransmissionServerConfig config, LocalFilePath localFilePath, RemoteFilePath remotePath, String filename);

    /**
     * 將記憶體內的檔案位元組資料直接上傳至遠端伺服器指定目錄 (零磁碟 I/O)
     *
     * @param config 伺服器連線配置
     * @param content 檔案位元組資料
     * @param remotePath 遠端目標目錄
     * @param filename 遠端目標檔案名稱
     */
    void uploadFromMemory(TransmissionServerConfig config, byte[] content, RemoteFilePath remotePath, String filename);

    /**
     * 從遠端伺服器下載檔案至本機指定路徑
     *
     * @param config 伺服器連線配置
     * @param remoteFilePath 遠端檔案完整路徑
     * @param savedPath 本機儲存目標路徑
     */
    void download(TransmissionServerConfig config, RemoteFilePath remoteFilePath, LocalFilePath savedPath);

    /**
     * 同步本機目錄與遠端目錄
     *
     * @param config 伺服器連線配置
     * @param localDir 本機目錄路徑
     * @param remoteDir 遠端目錄路徑
     * @param direction 同步方向 (LOCAL_TO_REMOTE 或 REMOTE_TO_LOCAL)
     * @param deleteRemoteExtras 若為 true 且方向為 LOCAL_TO_REMOTE，刪除遠端多餘檔案
     * @return 實際傳輸處理的檔案數量
     */
    int syncDirectory(
            TransmissionServerConfig config,
            LocalFilePath localDir,
            RemoteFilePath remoteDir,
            SyncDirection direction,
            boolean deleteRemoteExtras
    );

    /**
     * 列出遠端目錄中的所有檔案與子目錄資訊
     *
     * @param config 伺服器連線配置
     * @param remotePath 遠端目錄路徑
     * @return 遠端檔案中繼資料清單
     */
    List<RemoteFileInfo> listFiles(TransmissionServerConfig config, RemoteFilePath remotePath);

    /**
     * 刪除遠端伺服器上的單一檔案
     *
     * @param config 伺服器連線配置
     * @param remoteFilePath 欲刪除的遠端檔案路徑
     * @return 是否成功刪除
     */
    boolean deleteFile(TransmissionServerConfig config, RemoteFilePath remoteFilePath);

    /**
     * 遞迴刪除遠端目錄
     *
     * @param config 伺服器連線配置
     * @param remoteDirectoryPath 欲刪除的遠端目錄路徑
     * @return 是否成功刪除
     */
    boolean deleteDirectory(TransmissionServerConfig config, RemoteFilePath remoteDirectoryPath);

    /**
     * 檢查遠端伺服器上是否存在指定檔案
     *
     * @param config 伺服器連線配置
     * @param remoteFilePath 欲檢查的遠端檔案路徑
     * @return 若檔案存在且為一般檔案回傳 true，否則回傳 false
     */
    boolean existsFile(TransmissionServerConfig config, RemoteFilePath remoteFilePath);

    /**
     * 移動或重新命名遠端伺服器上的檔案 (可用於歷史版本備份與歸檔)
     *
     * @param config 伺服器連線配置
     * @param sourcePath 來源檔案路徑
     * @param targetPath 目標檔案路徑
     */
    void moveFile(TransmissionServerConfig config, RemoteFilePath sourcePath, RemoteFilePath targetPath);

    /**
     * 測試與伺服器之連線與帳密認證有效性
     *
     * @param config 伺服器連線配置
     * @return 連線與驗證是否成功
     */
    boolean testConnection(TransmissionServerConfig config);
}
