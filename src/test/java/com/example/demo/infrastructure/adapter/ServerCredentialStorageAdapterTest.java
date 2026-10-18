package com.example.demo.infrastructure.adapter;

import com.example.demo.application.port.out.CredentialCipherPort;
import com.example.demo.application.shared.command.CreateServerCredentialCommand;
import com.example.demo.application.shared.dto.ServerCredentialGottenResult;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import com.example.demo.infrastructure.persistence.credential.entity.ServerCredentialEntity;
import com.example.demo.infrastructure.persistence.credential.repository.ServerCredentialJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 伺服器傳輸憑證儲存適配器單元測試 (ServerCredentialStorageAdapterTest)
 * <p>
 * 遵循 AGENTS.md 測試規範，採用 BDD 與 Gherkin 語法驗證 AES-GCM 機密落盤加密與還原解析。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ServerCredentialStorageAdapterTest {

    @Mock
    private ServerCredentialJpaRepository serverCredentialJpaRepository;

    @Mock
    private CredentialCipherPort credentialCipherPort;

    private ServerCredentialStorageAdapter storageAdapter;

    @BeforeEach
    void setUp() {
        storageAdapter = new ServerCredentialStorageAdapter(serverCredentialJpaRepository, credentialCipherPort);
    }

    /**
     * Feature: 憑證持久化與透明加解密
     * Scenario: 建立憑證時自動使用 AES-GCM 加密明文密碼與私鑰
     * Given: 傳入包含明文密碼與明文私鑰之 CreateServerCredentialCommand
     * When: 呼叫 save 方法
     * Then: 適配器調用 cipherPort.encrypt 並將 ENC(...) 密文寫入 JPA 實體
     */
    @Test
    @DisplayName("情境：建立憑證時自動使用 AES-GCM 加密密文落盤")
    void testSaveEncryptsPlaintextSecrets() {
        CreateServerCredentialCommand command = new CreateServerCredentialCommand(
                "my-sftp",
                "192.168.1.100",
                22,
                "SFTP",
                "user1",
                "PlainPassword123",
                "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA...",
                "KeyPassphrase",
                "測試憑證"
        );

        when(serverCredentialJpaRepository.existsByAlias("my-sftp")).thenReturn(false);
        when(credentialCipherPort.isEncrypted(anyString())).thenReturn(false);
        when(credentialCipherPort.encrypt("PlainPassword123")).thenReturn("ENC(aes_encrypted_password)");
        when(credentialCipherPort.encrypt("-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA...")).thenReturn("ENC(aes_encrypted_key)");
        when(credentialCipherPort.encrypt("KeyPassphrase")).thenReturn("ENC(aes_encrypted_passphrase)");

        when(serverCredentialJpaRepository.save(any(ServerCredentialEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ServerCredentialGottenResult result = storageAdapter.save(command);

        assertThat(result).isNotNull();
        assertThat(result.alias()).isEqualTo("my-sftp");
        assertThat(result.maskedPassword()).isEqualTo("******");
        assertThat(result.hasPassword()).isTrue();
        assertThat(result.hasPrivateKey()).isTrue();

        ArgumentCaptor<ServerCredentialEntity> captor = ArgumentCaptor.forClass(ServerCredentialEntity.class);
        verify(serverCredentialJpaRepository).save(captor.capture());
        ServerCredentialEntity savedEntity = captor.getValue();

        assertThat(savedEntity.getEncryptedPassword()).isEqualTo("ENC(aes_encrypted_password)");
        assertThat(savedEntity.getEncryptedPrivateKey()).isEqualTo("ENC(aes_encrypted_key)");
        assertThat(savedEntity.getEncryptedPassphrase()).isEqualTo("ENC(aes_encrypted_passphrase)");
    }

    /**
     * Feature: 憑證持久化與透明加解密
     * Scenario: 傳輸任務依別名解析出解密後之 TransmissionServerConfig
     * Given: 資料庫中已存在加密之憑證實體
     * When: 傳輸引擎呼叫 resolveTransmissionConfig("my-sftp")
     * Then: 適配器在記憶體中還原明文並封裝為 TransmissionServerConfig 物件
     */
    @Test
    @DisplayName("情境：傳輸任務解析憑證時自動還原明文供連線引擎使用")
    void testResolveTransmissionConfigDecryptsSecrets() {
        ServerCredentialEntity mockEntity = new ServerCredentialEntity(
                "cred-999",
                "my-sftp",
                "127.0.0.1",
                2222,
                "SFTP",
                "tester",
                "ENC(aes_enc_pass)",
                "ENC(aes_enc_key)",
                null,
                "測試"
        );

        when(serverCredentialJpaRepository.findById("my-sftp")).thenReturn(Optional.empty());
        when(serverCredentialJpaRepository.findByAlias("my-sftp")).thenReturn(Optional.of(mockEntity));

        when(credentialCipherPort.decryptIfEncrypted("ENC(aes_enc_pass)")).thenReturn("password");
        when(credentialCipherPort.decryptIfEncrypted("ENC(aes_enc_key)")).thenReturn("my-decrypted-ssh-key");

        Optional<TransmissionServerConfig> configOpt = storageAdapter.resolveTransmissionConfig("my-sftp");

        assertThat(configOpt).isPresent();
        TransmissionServerConfig config = configOpt.get();
        assertThat(config.getHost()).isEqualTo("127.0.0.1");
        assertThat(config.getPort()).isEqualTo(2222);
        assertThat(config.getProtocol()).isEqualTo(TransmissionProtocol.SFTP);
        assertThat(config.getUsername()).isEqualTo("tester");
        assertThat(config.getPassword()).isEqualTo("password");
        assertThat(config.getPrivateKey()).isEqualTo("my-decrypted-ssh-key");
    }
}
