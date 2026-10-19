package com.example.demo.infrastructure.transmission.client;

import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFileInfo;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import java.util.List;

/**
 * 傳輸協定專屬客戶端內部介面 (TransmissionProtocolClient)
 * <p>
 * 位於基礎設施層內部，各具體通訊技術 (SFTP / FTP / FTPS) 需實作此介面，
 * 供 FileTransmissionGatewayAdapter 依協定策略分派呼叫。
 * </p>
 */
public interface TransmissionProtocolClient {

    /**
     * 判定本客戶端是否支援該傳輸協定
     *
     * @param protocol 協定列舉
     * @return 是否支援
     */
    boolean supports(TransmissionProtocol protocol);

    /**
     * 上傳檔案
     */
    void upload(TransmissionServerConfig config, LocalFilePath localFilePath, RemoteFilePath remotePath, String filename);

    /**
     * 將記憶體內的檔案位元組資料直接上傳至遠端伺服器指定目錄 (零磁碟 I/O)
     *
     * @param config 傳輸伺服器連線配置
     * @param content 檔案位元組資料
     * @param remotePath 遠端目標目錄路徑
     * @param filename 儲存於遠端之檔案名稱
     */
    void uploadFromMemory(TransmissionServerConfig config, byte[] content, RemoteFilePath remotePath, String filename);

    /**
     * 下載檔案
     */
    void download(TransmissionServerConfig config, RemoteFilePath remoteFilePath, LocalFilePath savedPath);

    /**
     * 同步目錄
     */
    int syncDirectory(
            TransmissionServerConfig config,
            LocalFilePath localDir,
            RemoteFilePath remoteDir,
            SyncDirection direction,
            boolean deleteRemoteExtras
    );

    /**
     * 列出遠端目錄檔案
     */
    List<RemoteFileInfo> listFiles(TransmissionServerConfig config, RemoteFilePath remotePath);

    /**
     * 刪除遠端檔案
     */
    boolean deleteFile(TransmissionServerConfig config, RemoteFilePath remoteFilePath);

    /**
     * 遞迴刪除遠端目錄
     */
    boolean deleteDirectory(TransmissionServerConfig config, RemoteFilePath remoteDirectoryPath);

    /**
     * 檢查遠端檔案是否存在
     */
    boolean existsFile(TransmissionServerConfig config, RemoteFilePath remoteFilePath);

    /**
     * 移動或重新命名遠端檔案
     */
    void moveFile(TransmissionServerConfig config, RemoteFilePath sourcePath, RemoteFilePath targetPath);

    /**
     * 取得遠端伺服器上指定檔案之位元組大小
     *
     * @param config 傳輸伺服器連線配置
     * @param remoteFilePath 遠端檔案路徑模型
     * @return 檔案位元組大小，若檔案不存在或為目錄則回傳 -1L
     */
    long getRemoteFileSize(TransmissionServerConfig config, RemoteFilePath remoteFilePath);

    /**
     * 測試連線
     */
    boolean testConnection(TransmissionServerConfig config);
}
