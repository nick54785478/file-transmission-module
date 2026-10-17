package com.example.demo.application.service;

import com.example.demo.application.assembler.TransmissionDtoAssembler;
import com.example.demo.application.port.in.GetTransmissionStatusUseCase;
import com.example.demo.application.port.in.ListRemoteFilesUseCase;
import com.example.demo.application.port.out.CredentialCipherPort;
import com.example.demo.application.port.out.FileTransmissionGatewayPort;
import com.example.demo.application.port.out.FileTransmissionTrackerPort;
import com.example.demo.application.shared.dto.FileTransmissionGottenResult;
import com.example.demo.application.shared.query.GetTransmissionStatusQuery;
import com.example.demo.application.shared.query.ListRemoteFilesQuery;
import com.example.demo.application.shared.view.RemoteFileGottenView;
import com.example.demo.application.shared.vo.RemoteFileInfo;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 遠端檔案與傳輸狀態查詢服務 (FileTransmissionQueryService - Inbound Adapter / Query Service)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>in : ... + QueryService</code> (AGENTS.md 3-9 CQRS)。</li>
 *   <li>實作 Inbound Ports: {@link ListRemoteFilesUseCase} 與 {@link GetTransmissionStatusUseCase}。</li>
 *   <li>透過 <code>CredentialCipherPort</code> 執行查詢請求中機密憑證的解密還原。</li>
 *   <li>負責調用傳輸閘道輸出埠 ({@link FileTransmissionGatewayPort}) 查詢遠端目錄檔案清單。</li>
 *   <li>負責調用追蹤輸出埠 ({@link FileTransmissionTrackerPort}) 查詢指定 trackingId 之即時或歷史傳輸狀態。</li>
 * </ul>
 * </p>
 */
@Service
public class FileTransmissionQueryService implements ListRemoteFilesUseCase, GetTransmissionStatusUseCase {

    private final FileTransmissionGatewayPort fileTransmissionGatewayPort;
    private final FileTransmissionTrackerPort fileTransmissionTrackerPort;
    private final CredentialCipherPort credentialCipherPort;

    /**
     * 向下相容建構子 (2 參數版本)：維持既有單元測試完全相容
     *
     * @param fileTransmissionGatewayPort 檔案傳輸通訊閘道輸出埠
     * @param fileTransmissionTrackerPort 檔案傳輸生命週期追蹤輸出埠
     */
    public FileTransmissionQueryService(
            FileTransmissionGatewayPort fileTransmissionGatewayPort,
            FileTransmissionTrackerPort fileTransmissionTrackerPort
    ) {
        this(fileTransmissionGatewayPort, fileTransmissionTrackerPort, CredentialCipherPort.NOOP);
    }

    /**
     * 建構檔案傳輸查詢應用服務 (完整依賴注入版本，供 Spring 容器自動裝配)
     *
     * @param fileTransmissionGatewayPort 檔案傳輸通訊閘道輸出埠
     * @param fileTransmissionTrackerPort 檔案傳輸生命週期追蹤輸出埠
     * @param credentialCipherPort 憑證機密加解密輸出埠
     */
    @org.springframework.beans.factory.annotation.Autowired
    public FileTransmissionQueryService(
            FileTransmissionGatewayPort fileTransmissionGatewayPort,
            FileTransmissionTrackerPort fileTransmissionTrackerPort,
            CredentialCipherPort credentialCipherPort
    ) {
        this.fileTransmissionGatewayPort = Objects.requireNonNull(fileTransmissionGatewayPort, "fileTransmissionGatewayPort 不能為空");
        this.fileTransmissionTrackerPort = Objects.requireNonNull(fileTransmissionTrackerPort, "fileTransmissionTrackerPort 不能為空");
        this.credentialCipherPort = Objects.requireNonNull(credentialCipherPort, "credentialCipherPort 不能為空");
    }

    @Override
    public List<RemoteFileGottenView> listRemoteFiles(ListRemoteFilesQuery query) {
        TransmissionServerConfig effectiveConfig = credentialCipherPort.decryptServerConfig(query.getServerConfig());
        List<RemoteFileInfo> fileInfoList = fileTransmissionGatewayPort.listFiles(
                effectiveConfig,
                query.getRemotePath()
        );
        return TransmissionDtoAssembler.toViewList(fileInfoList);
    }

    @Override
    public Optional<FileTransmissionGottenResult> getTransmissionStatus(GetTransmissionStatusQuery query) {
        return fileTransmissionTrackerPort.getTransmissionStatus(query.trackingId());
    }
}
