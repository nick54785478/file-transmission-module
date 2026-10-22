package com.example.demo.infrastructure.adapter;

import com.example.demo.application.port.out.ServerCredentialStoragePort;
import com.example.demo.application.shared.command.CreateServerCredentialCommand;
import com.example.demo.application.shared.command.UpdateServerCredentialCommand;
import com.example.demo.application.shared.dto.ServerCredentialGottenResult;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.example.demo.infrastructure.persistence.credential.entity.ServerCredentialEntity;
import com.example.demo.infrastructure.persistence.credential.repository.ServerCredentialJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 伺服器傳輸憑證儲存適配器 (ServerCredentialStorageAdapter - Outbound Adapter)
 * <p>
 * <b>架構與設計職責說明 (AGENTS.md 2 與 3-9)：</b>
 * <ul>
 * <li>命名規範: <code>out : ... + Adapter</code>。</li>
 * <li>實作應用層 Outbound Port ({@link ServerCredentialStoragePort})。</li>
 * <li>作為連線憑證儲存模組對外出口，專責對接並協調 <code>ServerCredentialJpaRepository</code> 執行 JPA 持久化操作。</li>
 * <li><b>純粹持久化職責：</b> 接收應用層已加密完成之 Command，直接落盤；對外查詢時提供遮罩脫敏之 DTO ({@link ServerCredentialGottenResult})。</li>
 * </ul>
 * </p>
 */
@Component
public class ServerCredentialStorageAdapter implements ServerCredentialStoragePort {

    private final ServerCredentialJpaRepository serverCredentialJpaRepository;

    public ServerCredentialStorageAdapter(ServerCredentialJpaRepository serverCredentialJpaRepository) {
        this.serverCredentialJpaRepository = Objects.requireNonNull(
                serverCredentialJpaRepository,
                "serverCredentialJpaRepository 不能為空");
    }

    @Override
    @Transactional
    public ServerCredentialGottenResult save(CreateServerCredentialCommand command) {
        if (serverCredentialJpaRepository.existsByAlias(command.alias())) {
            throw new IllegalArgumentException("憑證別名已存在，不可重複使用: " + command.alias());
        }

        ServerCredentialEntity entity = new ServerCredentialEntity(
                null,
                command.alias(),
                command.host(),
                command.port(),
                command.protocol(),
                command.username(),
                command.password(),
                command.privateKey(),
                command.passphrase(),
                command.description());

        ServerCredentialEntity saved = serverCredentialJpaRepository.save(entity);
        return toGottenResult(saved);
    }

    @Override
    @Transactional
    public ServerCredentialGottenResult update(UpdateServerCredentialCommand command) {
        ServerCredentialEntity entity = serverCredentialJpaRepository.findById(command.id())
                .orElseThrow(() -> new IllegalArgumentException("查無指定的伺服器憑證 [ID: " + command.id() + "]"));

        if (command.alias() != null
                && !command.alias().trim().equalsIgnoreCase(entity.getAlias())
                && serverCredentialJpaRepository.existsByAlias(command.alias())) {
            throw new IllegalArgumentException("憑證別名已存在，不可重複使用: " + command.alias());
        }

        entity.updateDetails(
                command.alias(),
                command.host(),
                command.port(),
                command.protocol(),
                command.username(),
                command.description());

        entity.updateEncryptedSecrets(command.password(), command.privateKey(), command.passphrase());

        if (command.active() != null) {
            if (Boolean.TRUE.equals(command.active())) {
                entity.activate();
            } else {
                entity.deactivate();
            }
        }

        ServerCredentialEntity updated = serverCredentialJpaRepository.save(entity);
        return toGottenResult(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ServerCredentialGottenResult> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        return serverCredentialJpaRepository.findById(id.trim()).map(this::toGottenResult);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ServerCredentialGottenResult> findByAlias(String alias) {
        if (alias == null || alias.trim().isEmpty()) {
            return Optional.empty();
        }
        return serverCredentialJpaRepository.findByAlias(alias.trim()).map(this::toGottenResult);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TransmissionServerConfig> resolveTransmissionConfig(String idOrAlias) {
        if (idOrAlias == null || idOrAlias.trim().isEmpty()) {
            return Optional.empty();
        }
        String key = idOrAlias.trim();
        Optional<ServerCredentialEntity> entityOpt = serverCredentialJpaRepository.findById(key);
        if (entityOpt.isEmpty()) {
            entityOpt = serverCredentialJpaRepository.findByAlias(key);
        }

        return entityOpt.filter(ServerCredentialEntity::isActive).map(entity -> {
            TransmissionProtocol protocol = resolveProtocol(entity.getProtocol());
            int port = (entity.getPort() != null && entity.getPort() > 0)
                    ? entity.getPort()
                    : protocol.getDefaultPort();

            return new TransmissionServerConfig(
                    entity.getHost() != null ? entity.getHost() : "127.0.0.1",
                    port,
                    protocol,
                    entity.getUsername(),
                    entity.getEncryptedPassword(),
                    10000,
                    true,
                    false,
                    entity.getEncryptedPrivateKey(),
                    entity.getEncryptedPassphrase());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServerCredentialGottenResult> findAll() {
        return findAllCredentials();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServerCredentialGottenResult> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return findAllCredentials();
        }
        return serverCredentialJpaRepository.searchByKeyword(keyword.trim()).stream()
                .map(this::toGottenResult)
                .toList();
    }

    /**
     * 查詢所有連線憑證並封裝為遮罩結果物件清單。
     * <p>
     * 抽離為內部輔助方法以避免在 {@code search(keyword)} 中經由 {@code this} 直接調用帶有 {@code @Transactional} 的 {@code findAll()} 方法，
     * 避免繞過 Spring AOP Proxy 導致事務傳播失效 (SonarQube S6809)。
     * </p>
     *
     * @return 脫敏後的伺服器憑證結果物件清單
     */
    private List<ServerCredentialGottenResult> findAllCredentials() {
        return serverCredentialJpaRepository.findAll().stream()
                .map(this::toGottenResult)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByAlias(String alias) {
        if (alias == null || alias.trim().isEmpty()) {
            return false;
        }
        return serverCredentialJpaRepository.existsByAlias(alias.trim());
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("憑證識別碼不能為空");
        }
        String key = id.trim();
        Optional<ServerCredentialEntity> entityOpt = serverCredentialJpaRepository.findById(key);
        if (entityOpt.isEmpty()) {
            entityOpt = serverCredentialJpaRepository.findByAlias(key);
        }
        ServerCredentialEntity entity = entityOpt
                .orElseThrow(() -> new IllegalArgumentException("查無指定的伺服器憑證 [ID/別名: " + key + "]"));

        entity.deactivate();
        serverCredentialJpaRepository.save(entity);
    }

    @Override
    @Transactional
    public ServerCredentialGottenResult reactivateById(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("憑證識別碼不能為空");
        }
        String key = id.trim();
        Optional<ServerCredentialEntity> entityOpt = serverCredentialJpaRepository.findById(key);
        if (entityOpt.isEmpty()) {
            entityOpt = serverCredentialJpaRepository.findByAlias(key);
        }
        ServerCredentialEntity entity = entityOpt
                .orElseThrow(() -> new IllegalArgumentException("查無指定的伺服器憑證 [ID/別名: " + key + "]"));

        entity.activate();
        ServerCredentialEntity saved = serverCredentialJpaRepository.save(entity);
        return toGottenResult(saved);
    }

    // --- 內部遮罩轉換輔助邏輯 ---

    private ServerCredentialGottenResult toGottenResult(ServerCredentialEntity entity) {
        boolean hasPass = entity.getEncryptedPassword() != null && !entity.getEncryptedPassword().trim().isEmpty();
        boolean hasKey = entity.getEncryptedPrivateKey() != null && !entity.getEncryptedPrivateKey().trim().isEmpty();
        boolean hasPassphrase = entity.getEncryptedPassphrase() != null
                && !entity.getEncryptedPassphrase().trim().isEmpty();

        String maskedPassword = hasPass ? "******" : null;
        String maskedKey = hasKey ? "[SSH_PRIVATE_KEY_CONFIGURED]" : null;
        String maskedPassphrase = hasPassphrase ? "******" : null;

        return new ServerCredentialGottenResult(
                entity.getId(),
                entity.getAlias(),
                entity.getHost(),
                entity.getPort(),
                entity.getProtocol(),
                entity.getUsername(),
                maskedPassword,
                maskedKey,
                maskedPassphrase,
                hasPass,
                hasKey,
                hasPassphrase,
                entity.isActive(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private static TransmissionProtocol resolveProtocol(String protocolStr) {
        if (protocolStr == null || protocolStr.trim().isEmpty()) {
            return TransmissionProtocol.SFTP;
        }
        try {
            return TransmissionProtocol.valueOf(protocolStr.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return TransmissionProtocol.SFTP;
        }
    }
}
