package com.example.demo.infrastructure.adapter;

import com.example.demo.application.port.out.CredentialCipherPort;
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
 *   <li>命名規範: <code>out : ... + Adapter</code>。</li>
 *   <li>實作應用層 Outbound Port ({@link ServerCredentialStoragePort})。</li>
 *   <li>作為連線憑證儲存模組對外出口，協調 <code>ServerCredentialJpaRepository</code> 與 <code>CredentialCipherPort</code>。</li>
 *   <li><b>透明加解密與資安防護：</b>
 *     <ul>
 *       <li>入庫儲存 (Save/Update) 時：自動將明文密碼、SSH 私鑰及 Passphrase 透過 AES-GCM 加密為 <code>ENC(...)</code> 格式密文後落盤。</li>
 *       <li>出庫對外查詢 (Find/Search) 時：提供遮罩脫敏之 DTO ({@link ServerCredentialGottenResult})，杜絕洩漏。</li>
 *       <li>內部傳輸調用 (resolveTransmissionConfig) 時：在記憶體中還原為明文並封裝為 {@link TransmissionServerConfig}，即插即用。</li>
 *     </ul>
 *   </li>
 * </ul>
 * </p>
 */
@Component
public class ServerCredentialStorageAdapter implements ServerCredentialStoragePort {

    private final ServerCredentialJpaRepository serverCredentialJpaRepository;
    private final CredentialCipherPort credentialCipherPort;

    public ServerCredentialStorageAdapter(ServerCredentialJpaRepository serverCredentialJpaRepository,
                                         CredentialCipherPort credentialCipherPort) {
        this.serverCredentialJpaRepository = Objects.requireNonNull(
                serverCredentialJpaRepository,
                "serverCredentialJpaRepository 不能為空"
        );
        this.credentialCipherPort = Objects.requireNonNull(
                credentialCipherPort,
                "credentialCipherPort 不能為空"
        );
    }

    @Override
    @Transactional
    public ServerCredentialGottenResult save(CreateServerCredentialCommand command) {
        if (serverCredentialJpaRepository.existsByAlias(command.alias())) {
            throw new IllegalArgumentException("憑證別名已存在，不可重複使用: " + command.alias());
        }

        String encPassword = encryptIfPresent(command.password());
        String encPrivateKey = encryptIfPresent(command.privateKey());
        String encPassphrase = encryptIfPresent(command.passphrase());

        ServerCredentialEntity entity = new ServerCredentialEntity(
                null,
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

        ServerCredentialEntity saved = serverCredentialJpaRepository.save(entity);
        return toGottenResult(saved);
    }

    @Override
    @Transactional
    public ServerCredentialGottenResult update(UpdateServerCredentialCommand command) {
        ServerCredentialEntity entity = serverCredentialJpaRepository.findById(command.id())
                .orElseThrow(() -> new IllegalArgumentException("查無指定的伺服器憑證 [ID: " + command.id() + "]"));

        if (command.alias() != null && !command.alias().trim().equalsIgnoreCase(entity.getAlias())) {
            if (serverCredentialJpaRepository.existsByAlias(command.alias())) {
                throw new IllegalArgumentException("憑證別名已存在，不可重複使用: " + command.alias());
            }
        }

        entity.updateDetails(
                command.alias(),
                command.host(),
                command.port(),
                command.protocol(),
                command.username(),
                command.description()
        );

        String newEncPassword = command.password() != null ? encryptIfPresent(command.password()) : null;
        String newEncPrivateKey = command.privateKey() != null ? encryptIfPresent(command.privateKey()) : null;
        String newEncPassphrase = command.passphrase() != null ? encryptIfPresent(command.passphrase()) : null;

        entity.updateEncryptedSecrets(newEncPassword, newEncPrivateKey, newEncPassphrase);

        if (command.active() != null) {
            if (command.active()) {
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
            String plainPassword = decryptIfPresent(entity.getEncryptedPassword());
            String plainPrivateKey = decryptIfPresent(entity.getEncryptedPrivateKey());
            String plainPassphrase = decryptIfPresent(entity.getEncryptedPassphrase());

            TransmissionProtocol protocol = resolveProtocol(entity.getProtocol());
            int port = (entity.getPort() != null && entity.getPort() > 0)
                    ? entity.getPort()
                    : protocol.getDefaultPort();

            return new TransmissionServerConfig(
                    entity.getHost() != null ? entity.getHost() : "127.0.0.1",
                    port,
                    protocol,
                    entity.getUsername(),
                    plainPassword,
                    10000,
                    true,
                    false,
                    plainPrivateKey,
                    plainPassphrase
            );
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServerCredentialGottenResult> findAll() {
        return serverCredentialJpaRepository.findAll().stream()
                .map(this::toGottenResult)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServerCredentialGottenResult> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return findAll();
        }
        return serverCredentialJpaRepository.searchByKeyword(keyword.trim()).stream()
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

    // --- 內部加解密與遮罩轉換輔助邏輯 ---

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

    private String decryptIfPresent(String encryptedText) {
        if (encryptedText == null || encryptedText.trim().isEmpty()) {
            return null;
        }
        return credentialCipherPort.decryptIfEncrypted(encryptedText.trim());
    }

    private ServerCredentialGottenResult toGottenResult(ServerCredentialEntity entity) {
        boolean hasPass = entity.getEncryptedPassword() != null && !entity.getEncryptedPassword().trim().isEmpty();
        boolean hasKey = entity.getEncryptedPrivateKey() != null && !entity.getEncryptedPrivateKey().trim().isEmpty();
        boolean hasPassphrase = entity.getEncryptedPassphrase() != null && !entity.getEncryptedPassphrase().trim().isEmpty();

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
                entity.getUpdatedAt()
        );
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
