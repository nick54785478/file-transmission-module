package com.example.demo.application.shared.command;

import java.io.Serializable;

/**
 * 更新伺服器憑證命令物件 (UpdateServerCredentialCommand - Command)
 * <p>
 * <b>架構與命名規範 (AGENTS.md 2 與 3-4)：</b>
 * 命名規範: <code>V + N + Command</code>。
 * 位於應用層 <code>application/shared/command/</code>，作為更新憑證的輸入參數載體。
 * </p>
 *
 * @param id 欲更新之憑證唯一識別碼，不可為空
 * @param alias 新別名
 * @param host 新主機位址
 * @param port 新通訊埠
 * @param protocol 新傳輸協定
 * @param username 新登入帳號
 * @param password 新密碼 (若為 null 則維持既有密碼)
 * @param privateKey 新 SSH 私鑰 (若為 null 則維持既有私鑰)
 * @param passphrase 新私鑰密碼
 * @param description 新備註說明
 * @param active 是否啟用 (選填)
 */
public record UpdateServerCredentialCommand(
        String id,
        String alias,
        String host,
        Integer port,
        String protocol,
        String username,
        String password,
        String privateKey,
        String passphrase,
        String description,
        Boolean active
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public UpdateServerCredentialCommand {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("憑證識別碼 id 不能為空");
        }
    }
}
