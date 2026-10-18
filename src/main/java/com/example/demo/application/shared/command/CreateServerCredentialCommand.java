package com.example.demo.application.shared.command;

import java.io.Serializable;

/**
 * 建立伺服器憑證命令物件 (CreateServerCredentialCommand - Command)
 * <p>
 * <b>架構與命名規範 (AGENTS.md 2 與 3-4)：</b>
 * 命名規範: <code>V + N + Command</code>。
 * 位於應用層 <code>application/shared/command/</code>，作為建立憑證的輸入參數載體。
 * </p>
 *
 * @param alias 憑證易讀別名 (唯一識別名稱)，不可為空
 * @param host 伺服器主機名稱或 IP 位址
 * @param port 通訊埠號 (選填)
 * @param protocol 傳輸協定 (SFTP / FTP / FTPS，選填)
 * @param username 登入使用者帳號，不可為空
 * @param password 登入密碼 (支援明文或 ENC(...) 加密密文)
 * @param privateKey SSH 私鑰內容 (PEM 格式) 或本機路徑 (支援明文或 ENC(...) 密文)
 * @param passphrase SSH 私鑰解密密碼
 * @param description 備註說明
 */
public record CreateServerCredentialCommand(
        String alias,
        String host,
        Integer port,
        String protocol,
        String username,
        String password,
        String privateKey,
        String passphrase,
        String description
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public CreateServerCredentialCommand {
        if (alias == null || alias.trim().isEmpty()) {
            throw new IllegalArgumentException("憑證別名 alias 不能為空");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("登入帳號 username 不能為空");
        }
        boolean hasPass = password != null && !password.trim().isEmpty();
        boolean hasKey = privateKey != null && !privateKey.trim().isEmpty();
        if (!hasPass && !hasKey) {
            throw new IllegalArgumentException("登入密碼 (password) 與 SSH 私鑰 (privateKey) 至少須提供其中一項");
        }
    }
}
