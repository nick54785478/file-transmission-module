package com.example.demo.application.shared.query;

import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;

/**
 * 查詢遠端檔案清單應用查詢物件 (ListRemoteFilesQuery)
 * <p>
 * 命名規範: V + N + Query (3-5)
 * 由表現層傳入 ListRemoteFilesUseCase 執行檔案目錄清單查詢。
 * </p>
 */
public class ListRemoteFilesQuery {

    private final TransmissionServerConfig serverConfig;
    private final RemoteFilePath remotePath;

    public ListRemoteFilesQuery(TransmissionServerConfig serverConfig, RemoteFilePath remotePath) {
        this.serverConfig = serverConfig;
        this.remotePath = remotePath;
    }

    public static ListRemoteFilesQuery of(
            String host,
            int port,
            TransmissionProtocol protocol,
            String username,
            String password,
            String remotePath
    ) {
        TransmissionServerConfig config = TransmissionServerConfig.of(host, port, protocol, username, password);
        return new ListRemoteFilesQuery(config, RemoteFilePath.of(remotePath));
    }

    public TransmissionServerConfig getServerConfig() {
        return serverConfig;
    }

    public RemoteFilePath getRemotePath() {
        return remotePath;
    }
}
