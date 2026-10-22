package com.example.demo.application.service;

import com.example.demo.application.port.in.CreateServerCredentialUseCase;
import com.example.demo.application.port.in.DeleteServerCredentialUseCase;
import com.example.demo.application.port.in.ReactivateServerCredentialUseCase;
import com.example.demo.application.port.in.UpdateServerCredentialUseCase;
import com.example.demo.application.port.out.CredentialCipherPort;
import com.example.demo.application.port.out.ServerCredentialStoragePort;
import com.example.demo.application.shared.command.CreateServerCredentialCommand;
import com.example.demo.application.shared.command.DeleteServerCredentialCommand;
import com.example.demo.application.shared.command.ReactivateServerCredentialCommand;
import com.example.demo.application.shared.command.UpdateServerCredentialCommand;
import com.example.demo.application.shared.dto.ServerCredentialGottenResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 伺服器傳輸憑證命令應用服務 (ServerCredentialCommandService - Inbound Adapter / Command Service)
 * <p>
 * <b>架構與設計職責說明 (AGENTS.md 2 與 3-9)：</b>
 * <ul>
 *   <li>命名規範: <code>in : ... + CommandService</code>。</li>
 *   <li>位於應用層 <code>application/service/</code>。</li>
 *   <li>實作 {@link CreateServerCredentialUseCase}、{@link UpdateServerCredentialUseCase}、
 *       {@link DeleteServerCredentialUseCase} 與 {@link ReactivateServerCredentialUseCase}。</li>
 *   <li>作為寫入異動操作 (Command) 之協調者：
 *     <ul>
 *       <li>調用 {@link CredentialCipherPort} 將明文密碼、SSH 私鑰及通行碼加密為 <code>ENC(...)</code> 格式密文。</li>
 *       <li>調用 {@link ServerCredentialStoragePort} 輸出埠執行業務規則、軟刪除、重生效與持久化落盤。</li>
 *     </ul>
 *   </li>
 * </ul>
 * </p>
 */
@Service
public class ServerCredentialCommandService implements
        CreateServerCredentialUseCase,
        UpdateServerCredentialUseCase,
        DeleteServerCredentialUseCase,
        ReactivateServerCredentialUseCase {

    private final ServerCredentialStoragePort serverCredentialStoragePort;
    private final CredentialCipherPort credentialCipherPort;

    /**
     * 單參數建構子 (供單元測試或無加密情境相容裝配，預設使用 CredentialCipherPort.NOOP)
     *
     * @param serverCredentialStoragePort 伺服器傳輸憑證儲存輸出埠
     */
    public ServerCredentialCommandService(ServerCredentialStoragePort serverCredentialStoragePort) {
        this(serverCredentialStoragePort, CredentialCipherPort.NOOP);
    }

    /**
     * 完整依賴注入建構子 (供 Spring 容器自動裝配)
     *
     * @param serverCredentialStoragePort 伺服器傳輸憑證儲存輸出埠
     * @param credentialCipherPort        憑證機密加解密輸出埠
     */
    @Autowired
    public ServerCredentialCommandService(
            ServerCredentialStoragePort serverCredentialStoragePort,
            CredentialCipherPort credentialCipherPort
    ) {
        this.serverCredentialStoragePort = Objects.requireNonNull(
                serverCredentialStoragePort,
                "serverCredentialStoragePort 不能為空"
        );
        this.credentialCipherPort = Objects.requireNonNull(
                credentialCipherPort,
                "credentialCipherPort 不能為空"
        );
    }

    @Override
    public ServerCredentialGottenResult createCredential(CreateServerCredentialCommand command) {
        Objects.requireNonNull(command, "CreateServerCredentialCommand 不能為空");

        String encPassword = encryptIfPresent(command.password());
        String encPrivateKey = encryptIfPresent(command.privateKey());
        String encPassphrase = encryptIfPresent(command.passphrase());

        CreateServerCredentialCommand encryptedCommand = new CreateServerCredentialCommand(
                command.alias(),
                command.host(),
                command.port(),
                command.protocol(),
                command.username(),
                encPassword,
                encPrivateKey,
                encPassphrase,
                command.description()
        );
        return serverCredentialStoragePort.save(encryptedCommand);
    }

    @Override
    public ServerCredentialGottenResult updateCredential(UpdateServerCredentialCommand command) {
        Objects.requireNonNull(command, "UpdateServerCredentialCommand 不能為空");

        String encPassword = command.password() != null ? encryptIfPresent(command.password()) : null;
        String encPrivateKey = command.privateKey() != null ? encryptIfPresent(command.privateKey()) : null;
        String encPassphrase = command.passphrase() != null ? encryptIfPresent(command.passphrase()) : null;

        UpdateServerCredentialCommand encryptedCommand = new UpdateServerCredentialCommand(
                command.id(),
                command.alias(),
                command.host(),
                command.port(),
                command.protocol(),
                command.username(),
                encPassword,
                encPrivateKey,
                encPassphrase,
                command.description(),
                command.active()
        );
        return serverCredentialStoragePort.update(encryptedCommand);
    }

    @Override
    public void deleteCredential(DeleteServerCredentialCommand command) {
        Objects.requireNonNull(command, "DeleteServerCredentialCommand 不能為空");
        serverCredentialStoragePort.deleteById(command.id());
    }

    @Override
    public ServerCredentialGottenResult reactivateCredential(ReactivateServerCredentialCommand command) {
        Objects.requireNonNull(command, "ReactivateServerCredentialCommand 不能為空");
        return serverCredentialStoragePort.reactivateById(command.id());
    }

    private String encryptIfPresent(String plainOrEnc) {
        if (plainOrEnc == null || plainOrEnc.trim().isEmpty()) {
            return null;
        }
        String trimmed = plainOrEnc.trim();
        if (credentialCipherPort.isEncrypted(trimmed)) {
            return trimmed;
        }
        return credentialCipherPort.encrypt(trimmed);
    }
}
