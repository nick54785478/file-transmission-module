package com.example.demo.application.service;

import com.example.demo.application.port.in.CreateServerCredentialUseCase;
import com.example.demo.application.port.in.DeleteServerCredentialUseCase;
import com.example.demo.application.port.in.UpdateServerCredentialUseCase;
import com.example.demo.application.port.out.ServerCredentialStoragePort;
import com.example.demo.application.shared.command.CreateServerCredentialCommand;
import com.example.demo.application.shared.command.DeleteServerCredentialCommand;
import com.example.demo.application.shared.command.UpdateServerCredentialCommand;
import com.example.demo.application.shared.dto.ServerCredentialGottenResult;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 伺服器傳輸憑證命令應用服務 (ServerCredentialCommandService - Inbound Adapter / Command Service)
 * <p>
 * <b>架構與設計職責說明 (AGENTS.md 2 與 3-9)：</b>
 * <ul>
 *   <li>命名規範: <code>in : ... + CommandService</code>。</li>
 *   <li>位於應用層 <code>application/service/</code>。</li>
 *   <li>實作 {@link CreateServerCredentialUseCase}、{@link UpdateServerCredentialUseCase} 與 {@link DeleteServerCredentialUseCase}。</li>
 *   <li>作為寫入異動操作 (Command) 之協調者，調用 {@link ServerCredentialStoragePort} 輸出埠執行業務規則與持久化。</li>
 * </ul>
 * </p>
 */
@Service
public class ServerCredentialCommandService implements
        CreateServerCredentialUseCase,
        UpdateServerCredentialUseCase,
        DeleteServerCredentialUseCase {

    private final ServerCredentialStoragePort serverCredentialStoragePort;

    public ServerCredentialCommandService(ServerCredentialStoragePort serverCredentialStoragePort) {
        this.serverCredentialStoragePort = Objects.requireNonNull(
                serverCredentialStoragePort,
                "serverCredentialStoragePort 不能為空"
        );
    }

    @Override
    public ServerCredentialGottenResult createCredential(CreateServerCredentialCommand command) {
        Objects.requireNonNull(command, "CreateServerCredentialCommand 不能為空");
        return serverCredentialStoragePort.save(command);
    }

    @Override
    public ServerCredentialGottenResult updateCredential(UpdateServerCredentialCommand command) {
        Objects.requireNonNull(command, "UpdateServerCredentialCommand 不能為空");
        return serverCredentialStoragePort.update(command);
    }

    @Override
    public void deleteCredential(DeleteServerCredentialCommand command) {
        Objects.requireNonNull(command, "DeleteServerCredentialCommand 不能為空");
        serverCredentialStoragePort.deleteById(command.id());
    }
}
