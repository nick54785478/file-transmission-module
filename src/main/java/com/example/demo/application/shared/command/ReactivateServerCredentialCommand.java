package com.example.demo.application.shared.command;

import java.io.Serializable;
import java.util.Objects;

/**
 * 重新啟用/生效伺服器憑證命令 (ReactivateServerCredentialCommand - Command)
 * <p>
 * <b>架構與命名規範 (AGENTS.md 2 與 3-4)：</b>
 * <ul>
 *   <li>命名規範: <code>V + N + Command</code> (動詞 Reactivate + 名詞 ServerCredential + Command)。</li>
 *   <li>位於應用層 <code>application/shared/command/</code>。</li>
 *   <li><b>純資料載體 (Pure Data Carrier)：</b> 以不可變 Java Record 實作，作為重生效憑證之命令載體。</li>
 * </ul>
 * </p>
 *
 * @param id 憑證唯一識別碼或別名 (必填)
 */
public record ReactivateServerCredentialCommand(
        String id
) implements Serializable {

    private static final long serialVersionUID = 1L;

    public ReactivateServerCredentialCommand {
        Objects.requireNonNull(id, "憑證識別碼 (id) 不能為空");
        if (id.trim().isEmpty()) {
            throw new IllegalArgumentException("憑證識別碼 (id) 不能為空白字串");
        }
    }
}
