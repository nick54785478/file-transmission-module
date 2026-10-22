package com.example.demo.application.service;

import com.example.demo.application.port.in.GetServerCredentialUseCase;
import com.example.demo.application.port.in.ListServerCredentialsUseCase;
import com.example.demo.application.port.out.ServerCredentialStoragePort;
import com.example.demo.application.shared.dto.ServerCredentialGottenResult;
import com.example.demo.application.shared.query.GetServerCredentialQuery;
import com.example.demo.application.shared.query.ListServerCredentialsQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * 伺服器傳輸憑證查詢應用服務 (ServerCredentialQueryService - Inbound Adapter / Query Service)
 * <p>
 * <b>架構與設計職責說明 (AGENTS.md 2 與 3-9)：</b>
 * <ul>
 *   <li>命名規範: <code>in : ... + QueryService</code>。</li>
 *   <li>位於應用層 <code>application/service/</code>。</li>
 *   <li>實作 {@link GetServerCredentialUseCase} 與 {@link ListServerCredentialsUseCase}。</li>
 *   <li>專門負責只讀查詢作業 (CQRS Query Side)，回傳純資料載體 DTO ({@link ServerCredentialGottenResult})。</li>
 * </ul>
 * </p>
 */
@Service
public class ServerCredentialQueryService implements
        GetServerCredentialUseCase,
        ListServerCredentialsUseCase {

    private final ServerCredentialStoragePort serverCredentialStoragePort;

    public ServerCredentialQueryService(ServerCredentialStoragePort serverCredentialStoragePort) {
        this.serverCredentialStoragePort = Objects.requireNonNull(
                serverCredentialStoragePort,
                "serverCredentialStoragePort 不能為空"
        );
    }

    @Override
    public ServerCredentialGottenResult getCredential(GetServerCredentialQuery query) {
        Objects.requireNonNull(query, "GetServerCredentialQuery 不能為空");

        Optional<ServerCredentialGottenResult> resultOpt = Optional.empty();
        if (query.id() != null && !query.id().trim().isEmpty()) {
            resultOpt = serverCredentialStoragePort.findById(query.id());
        } else if (query.alias() != null && !query.alias().trim().isEmpty()) {
            resultOpt = serverCredentialStoragePort.findByAlias(query.alias());
        } else {
            throw new IllegalArgumentException("查詢憑證必須指定 id 或 alias");
        }

        return resultOpt.orElseThrow(() -> new NoSuchElementException("查無指定的伺服器憑證: " +
                (query.id() != null ? "ID=" + query.id() : "Alias=" + query.alias())));
    }

    @Override
    public List<ServerCredentialGottenResult> listCredentials(ListServerCredentialsQuery query) {
        List<ServerCredentialGottenResult> results = new ArrayList<>();
        if (query == null || query.keyword() == null || query.keyword().trim().isEmpty()) {
            results.addAll(serverCredentialStoragePort.findAll());
        } else {
            results.addAll( serverCredentialStoragePort.search(query.keyword()));
        }

        if (query != null && query.active() != null) {
            boolean targetActive = query.active();
            return results.stream()
                    .filter(item -> item.active() == targetActive)
                    .toList();
        }
        return results;
    }
}
