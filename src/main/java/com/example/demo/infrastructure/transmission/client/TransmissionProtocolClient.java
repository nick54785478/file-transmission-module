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
     * 測試連線
     */
    boolean testConnection(TransmissionServerConfig config);
}
