package com.example.demo.application.service;

import com.example.demo.application.port.out.CredentialCipherPort;
import com.example.demo.application.port.out.ServerCredentialStoragePort;
import com.example.demo.application.shared.command.CreateServerCredentialCommand;
import com.example.demo.application.shared.command.DeleteServerCredentialCommand;
import com.example.demo.application.shared.command.ReactivateServerCredentialCommand;
import com.example.demo.application.shared.command.UpdateServerCredentialCommand;
import com.example.demo.application.shared.dto.ServerCredentialGottenResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 伺服器傳輸憑證命令服務單元測試 (ServerCredentialCommandServiceTest)
 * <p>
 * 遵循 AGENTS.md 測試規範，採用 BDD 與 Gherkin 語法標註業務情境。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ServerCredentialCommandServiceTest {

    @Mock
    private ServerCredentialStoragePort serverCredentialStoragePort;

    @Mock
    private CredentialCipherPort credentialCipherPort;

    private ServerCredentialCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new ServerCredentialCommandService(serverCredentialStoragePort);
    }

    /**
     * Feature: 伺服器傳輸憑證管理
     * Scenario: 建立全新伺服器憑證成功
     * Given: 傳入合法之別名、帳號與密碼之 CreateServerCredentialCommand
     * When: 呼叫 createCredential 業務方法
     * Then: 委派儲存埠成功儲存並回傳遮罩後之 ServerCredentialGottenResult
     */
    @Test
    @DisplayName("情境：成功建立全新伺服器憑證")
    void testCreateCredentialSuccess() {
        CreateServerCredentialCommand command = new CreateServerCredentialCommand(
                "sftp-prod",
                "sftp.example.com",
                22,
                "SFTP",
                "admin",
                "Secret@123",
                null,
                null,
                "生產環境 SFTP 憑證"
        );

        ServerCredentialGottenResult expectedResult = new ServerCredentialGottenResult(
                "cred-101",
                "sftp-prod",
                "sftp.example.com",
                22,
                "SFTP",
                "admin",
                "******",
                null,
                null,
                true,
                false,
                false,
                true,
                "生產環境 SFTP 憑證",
                Instant.now(),
                Instant.now()
        );

        when(serverCredentialStoragePort.save(any(CreateServerCredentialCommand.class)))
                .thenReturn(expectedResult);

        ServerCredentialGottenResult actual = commandService.createCredential(command);

        assertThat(actual).isNotNull();
        assertThat(actual.id()).isEqualTo("cred-101");
        assertThat(actual.alias()).isEqualTo("sftp-prod");
        assertThat(actual.maskedPassword()).isEqualTo("******");
        assertThat(actual.hasPassword()).isTrue();
        verify(serverCredentialStoragePort).save(command);
    }

    /**
     * Feature: 伺服器傳輸憑證管理
     * Scenario: 建立憑證時別名已存在拋出重複例外
     * Given: 傳入已存在之 alias
     * When: 呼叫 createCredential 業務方法
     * Then: 儲存埠檢查重複並拋出 IllegalArgumentException
     */
    @Test
    @DisplayName("情境：建立憑證時別名已存在拋出例外")
    void testCreateCredentialDuplicateAliasThrowsException() {
        CreateServerCredentialCommand command = new CreateServerCredentialCommand(
                "duplicate-alias",
                "127.0.0.1",
                2222,
                "SFTP",
                "tester",
                "password",
                null,
                null,
                "重複別名測試"
        );

        when(serverCredentialStoragePort.save(any(CreateServerCredentialCommand.class)))
                .thenThrow(new IllegalArgumentException("憑證別名已存在，不可重複使用: duplicate-alias"));

        assertThatThrownBy(() -> commandService.createCredential(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("憑證別名已存在");
    }

    /**
     * Feature: 伺服器傳輸憑證管理
     * Scenario: 更新既有憑證資訊成功
     * Given: 傳入有效之 UpdateServerCredentialCommand
     * When: 呼叫 updateCredential 業務方法
     * Then: 委派儲存埠更新並回傳更新後之 DTO
     */
    @Test
    @DisplayName("情境：成功更新既有憑證資訊")
    void testUpdateCredentialSuccess() {
        UpdateServerCredentialCommand command = new UpdateServerCredentialCommand(
                "cred-101",
                "sftp-prod-renamed",
                "sftp2.example.com",
                2222,
                "SFTP",
                "admin2",
                "NewSecret!456",
                null,
                null,
                "更新後的描述",
                true
        );

        ServerCredentialGottenResult updatedResult = new ServerCredentialGottenResult(
                "cred-101",
                "sftp-prod-renamed",
                "sftp2.example.com",
                2222,
                "SFTP",
                "admin2",
                "******",
                null,
                null,
                true,
                false,
                false,
                true,
                "更新後的描述",
                Instant.now(),
                Instant.now()
        );

        when(serverCredentialStoragePort.update(any(UpdateServerCredentialCommand.class)))
                .thenReturn(updatedResult);

        ServerCredentialGottenResult actual = commandService.updateCredential(command);

        assertThat(actual).isNotNull();
        assertThat(actual.alias()).isEqualTo("sftp-prod-renamed");
        assertThat(actual.username()).isEqualTo("admin2");
        verify(serverCredentialStoragePort).update(command);
    }

    /**
     * Feature: 伺服器傳輸憑證管理
     * Scenario: 成功軟刪除指定 ID 之憑證
     * Given: 傳入待刪除之憑證 ID
     * When: 呼叫 deleteCredential 業務方法
     * Then: 委派儲存埠執行軟刪除
     */
    @Test
    @DisplayName("情境：成功軟刪除指定憑證")
    void testDeleteCredentialSuccess() {
        DeleteServerCredentialCommand command = new DeleteServerCredentialCommand("cred-to-delete");

        commandService.deleteCredential(command);

        verify(serverCredentialStoragePort).deleteById("cred-to-delete");
    }

    /**
     * Feature: 伺服器傳輸憑證管理
     * Scenario: 成功重新啟用/生效指定 ID 之憑證
     * Given: 傳入欲重生效之憑證 ID 之 ReactivateServerCredentialCommand
     * When: 呼叫 reactivateCredential 業務方法
     * Then: 委派儲存埠執行 reactivateById 並回傳重生效結果 DTO
     */
    @Test
    @DisplayName("情境：成功重新啟用/生效指定憑證")
    void testReactivateCredentialSuccess() {
        ReactivateServerCredentialCommand command = new ReactivateServerCredentialCommand("cred-to-reactivate");
        ServerCredentialGottenResult mockResult = new ServerCredentialGottenResult(
                "cred-to-reactivate",
                "sftp-reactivated",
                "127.0.0.1",
                2222,
                "SFTP",
                "admin",
                "******",
                null,
                null,
                true,
                false,
                false,
                true,
                "已重生效",
                Instant.now(),
                Instant.now()
        );

        when(serverCredentialStoragePort.reactivateById("cred-to-reactivate")).thenReturn(mockResult);

        ServerCredentialGottenResult actual = commandService.reactivateCredential(command);

        assertThat(actual).isNotNull();
        assertThat(actual.active()).isTrue();
        assertThat(actual.alias()).isEqualTo("sftp-reactivated");
        verify(serverCredentialStoragePort).reactivateById("cred-to-reactivate");
    }

    /**
     * Feature: 伺服器傳輸憑證管理
     * Scenario: 建立憑證時調用加解密埠加密明文敏感資訊
     * Given: 傳入含有明文密碼、SSH 私鑰及通行碼之 CreateServerCredentialCommand，並裝配真實/Mock之 CredentialCipherPort
     * When: 呼叫 createCredential 業務方法
     * Then: 應用層命令服務主動協調 CredentialCipherPort 加密各敏感欄位，並將包裝後的加密命令傳遞給儲存埠
     */
    @Test
    @DisplayName("情境：建立憑證時主動調用加解密埠加密敏感資訊")
    void testCreateCredentialEncryptsSecretsWithInjectedCipher() {
        ServerCredentialCommandService serviceWithCipher =
                new ServerCredentialCommandService(serverCredentialStoragePort, credentialCipherPort);

        CreateServerCredentialCommand command = new CreateServerCredentialCommand(
                "sftp-secure",
                "secure.example.com",
                22,
                "SFTP",
                "deployer",
                "plain-pass",
                "plain-key",
                "plain-phrase",
                "機密測試憑證"
        );

        when(credentialCipherPort.isEncrypted("plain-pass")).thenReturn(false);
        when(credentialCipherPort.encrypt("plain-pass")).thenReturn("ENC(enc-pass)");

        when(credentialCipherPort.isEncrypted("plain-key")).thenReturn(false);
        when(credentialCipherPort.encrypt("plain-key")).thenReturn("ENC(enc-key)");

        when(credentialCipherPort.isEncrypted("plain-phrase")).thenReturn(false);
        when(credentialCipherPort.encrypt("plain-phrase")).thenReturn("ENC(enc-phrase)");

        when(serverCredentialStoragePort.save(any(CreateServerCredentialCommand.class))).thenReturn(null);

        serviceWithCipher.createCredential(command);

        ArgumentCaptor<CreateServerCredentialCommand> captor =
                ArgumentCaptor.forClass(CreateServerCredentialCommand.class);
        verify(serverCredentialStoragePort).save(captor.capture());

        CreateServerCredentialCommand captured = captor.getValue();
        assertThat(captured.password()).isEqualTo("ENC(enc-pass)");
        assertThat(captured.privateKey()).isEqualTo("ENC(enc-key)");
        assertThat(captured.passphrase()).isEqualTo("ENC(enc-phrase)");
    }

    /**
     * Feature: 伺服器傳輸憑證管理
     * Scenario: 建立憑證時若密碼已為密文則不重複加密 (冪等保護)
     * Given: 傳入已為 ENC(...) 格式密文之 CreateServerCredentialCommand
     * When: 呼叫 createCredential 業務方法
     * Then: 不重複調用 encrypt 方法，直接保留原有密文
     */
    @Test
    @DisplayName("情境：建立憑證時若密碼已為密文則不重複加密")
    void testCreateCredentialWithAlreadyEncryptedSecretDoesNotReEncrypt() {
        ServerCredentialCommandService serviceWithCipher =
                new ServerCredentialCommandService(serverCredentialStoragePort, credentialCipherPort);

        CreateServerCredentialCommand command = new CreateServerCredentialCommand(
                "sftp-secure",
                "secure.example.com",
                22,
                "SFTP",
                "deployer",
                "ENC(already-encrypted-secret)",
                null,
                null,
                "已加密密碼測試"
        );

        when(credentialCipherPort.isEncrypted("ENC(already-encrypted-secret)")).thenReturn(true);
        when(serverCredentialStoragePort.save(any(CreateServerCredentialCommand.class))).thenReturn(null);

        serviceWithCipher.createCredential(command);

        verify(credentialCipherPort, never()).encrypt(any());

        ArgumentCaptor<CreateServerCredentialCommand> captor =
                ArgumentCaptor.forClass(CreateServerCredentialCommand.class);
        verify(serverCredentialStoragePort).save(captor.capture());
        assertThat(captor.getValue().password()).isEqualTo("ENC(already-encrypted-secret)");
    }

    /**
     * Feature: 伺服器傳輸憑證管理
     * Scenario: 更新既有憑證時對敏感機密資訊執行加密
     * Given: 傳入含有明文密碼之 UpdateServerCredentialCommand
     * When: 呼叫 updateCredential 業務方法
     * Then: 應用層命令服務調用 CredentialCipherPort 加密後傳遞給儲存埠
     */
    @Test
    @DisplayName("情境：更新既有憑證時主動調用加解密埠加密敏感資訊")
    void testUpdateCredentialEncryptsSecretsWithInjectedCipher() {
        ServerCredentialCommandService serviceWithCipher =
                new ServerCredentialCommandService(serverCredentialStoragePort, credentialCipherPort);

        UpdateServerCredentialCommand command = new UpdateServerCredentialCommand(
                "cred-101",
                "sftp-updated",
                "sftp.example.com",
                22,
                "SFTP",
                "admin",
                "new-plain-pass",
                null,
                null,
                "更新描述",
                true
        );

        when(credentialCipherPort.isEncrypted("new-plain-pass")).thenReturn(false);
        when(credentialCipherPort.encrypt("new-plain-pass")).thenReturn("ENC(new-enc-pass)");
        when(serverCredentialStoragePort.update(any(UpdateServerCredentialCommand.class))).thenReturn(null);

        serviceWithCipher.updateCredential(command);

        ArgumentCaptor<UpdateServerCredentialCommand> captor =
                ArgumentCaptor.forClass(UpdateServerCredentialCommand.class);
        verify(serverCredentialStoragePort).update(captor.capture());

        assertThat(captor.getValue().password()).isEqualTo("ENC(new-enc-pass)");
    }
}
