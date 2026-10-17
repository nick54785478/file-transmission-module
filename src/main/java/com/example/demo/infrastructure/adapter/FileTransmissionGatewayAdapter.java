package com.example.demo.infrastructure.adapter;

import com.example.demo.application.port.out.FileTransmissionGatewayPort;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFileInfo;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.example.demo.infrastructure.transmission.factory.TransmissionClientFactory;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 檔案傳輸閘道基礎設施適配器 (FileTransmissionGatewayAdapter - Outbound Adapter)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>out : ... + Adapter</code> (AGENTS.md 3-9)。</li>
 *   <li>實作應用層 Outbound Port (FileTransmissionGatewayPort)。</li>
 *   <li>作為所有檔案傳輸技術客戶端 (FTP, FTPS, SFTP) 的統一匯聚點與對外通訊閘道，
 *       依賴 {@link TransmissionClientFactory} 取得相應的協定客戶端並執行實體通訊。</li>
 *   <li>使表現層與應用層無需關心具體協定客戶端的建構與管理，達成 Clean Architecture 關注點分離。</li>
 * </ul>
 * </p>
 */
@Component
public class FileTransmissionGatewayAdapter implements FileTransmissionGatewayPort {

    private final TransmissionClientFactory clientFactory;

    /**
     * 建構檔案傳輸閘道適配器並注入客戶端工廠
     *
     * @param clientFactory 傳輸協定客戶端工廠
     */
    public FileTransmissionGatewayAdapter(TransmissionClientFactory clientFactory) {
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory 不能為空");
    }

    @Override
    public void upload(TransmissionServerConfig config, LocalFilePath localFilePath, RemoteFilePath remotePath, String filename) {
        clientFactory.getClient(config.getProtocol()).upload(config, localFilePath, remotePath, filename);
    }

    @Override
    public void download(TransmissionServerConfig config, RemoteFilePath remoteFilePath, LocalFilePath savedPath) {
        clientFactory.getClient(config.getProtocol()).download(config, remoteFilePath, savedPath);
    }

    @Override
    public int syncDirectory(
            TransmissionServerConfig config,
            LocalFilePath localDir,
            RemoteFilePath remoteDir,
            SyncDirection direction,
            boolean deleteRemoteExtras
    ) {
        return clientFactory.getClient(config.getProtocol())
                .syncDirectory(config, localDir, remoteDir, direction, deleteRemoteExtras);
    }

    @Override
    public List<RemoteFileInfo> listFiles(TransmissionServerConfig config, RemoteFilePath remotePath) {
        return clientFactory.getClient(config.getProtocol()).listFiles(config, remotePath);
    }

    @Override
    public boolean deleteFile(TransmissionServerConfig config, RemoteFilePath remoteFilePath) {
        return clientFactory.getClient(config.getProtocol()).deleteFile(config, remoteFilePath);
    }

    @Override
    public boolean deleteDirectory(TransmissionServerConfig config, RemoteFilePath remoteDirectoryPath) {
        return clientFactory.getClient(config.getProtocol()).deleteDirectory(config, remoteDirectoryPath);
    }

    @Override
    public boolean testConnection(TransmissionServerConfig config) {
        return clientFactory.getClient(config.getProtocol()).testConnection(config);
    }
}
