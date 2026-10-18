package com.example.demo.application.shared.command;

import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import java.util.Objects;

/**
 * 記憶體檔案上傳應用命令 (UploadFileFromMemoryCommand - Record)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>V + N + Command</code> (AGENTS.md 3-4)。</li>
 *   <li>代表應用層執行記憶體檔案資料直接上傳遠端（Zero Disk I/O）的不可變命令載體 (Immutable Command Record)。</li>
 *   <li>封裝純 Java 二進位位元組陣列 (<code>byte[]</code>) 與領域數值物件，嚴禁依賴任何 Spring 或 Web 框架物件。</li>
 *   <li>由表現層防腐轉換器 (<code>TransmissionResourceAssembler</code>) 將 HTTP Multipart 請求轉為此命令，
 *       傳遞給 <code>UploadFileFromMemoryUseCase</code> 進行傳輸調度。</li>
 * </ul>
 * </p>
 *
 * @param serverConfig 遠端伺服器連線與驗證配置
 * @param fileContent 待上傳之二進位檔案內容 (記憶體直傳，不落盤)
 * @param remotePath 遠端目標儲存目錄路徑
 * @param filename 遠端目標儲存檔名
 */
public record UploadFileFromMemoryCommand(
        TransmissionServerConfig serverConfig,
        byte[] fileContent,
        RemoteFilePath remotePath,
        String filename
) {

    /**
     * Compact Constructor：執行參數非空值與有效性校驗
     */
    public UploadFileFromMemoryCommand {
        Objects.requireNonNull(serverConfig, "serverConfig 不能為空");
        Objects.requireNonNull(fileContent, "fileContent 不能為空");
        Objects.requireNonNull(remotePath, "remotePath 不能為空");
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("filename 不能為空");
        }
        filename = filename.trim();
    }

    /**
     * 便捷工廠方法：透過基本型別參數快速構建 UploadFileFromMemoryCommand
     *
     * @param host 主機位址
     * @param port 埠號
     * @param protocol 通訊協定 (FTP, FTPS, SFTP)
     * @param username 登入帳號
     * @param password 登入密碼
     * @param fileContent 記憶體二進位檔案內容
     * @param remotePath 遠端目標目錄路徑字串
     * @param filename 目標檔名
     * @return UploadFileFromMemoryCommand 不可變命令物件
     */
    public static UploadFileFromMemoryCommand of(
            String host,
            int port,
            TransmissionProtocol protocol,
            String username,
            String password,
            byte[] fileContent,
            String remotePath,
            String filename
    ) {
        TransmissionServerConfig config = TransmissionServerConfig.of(host, port, protocol, username, password);
        return new UploadFileFromMemoryCommand(config, fileContent, RemoteFilePath.of(remotePath), filename);
    }
}
