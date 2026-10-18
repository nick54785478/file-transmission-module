package com.example.demo.application.shared.command;

import java.io.Serializable;

/**
 * 刪除伺服器憑證命令物件 (DeleteServerCredentialCommand - Command)
 * <p>
 * <b>架構與命名規範 (AGENTS.md 2 與 3-4)：</b>
 * 命名規範: <code>V + N + Command</code>。
 * 位於應用層 <code>application/shared/command/</code>，攜帶欲刪除之憑證識別碼。
 * </p>
 *
 * @param id 欲刪除之憑證唯一識別碼，不可為空
 */
public record DeleteServerCredentialCommand(
        String id
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public DeleteServerCredentialCommand {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("刪除憑證識別碼 id 不能為空");
        }
    }
}
