package com.example.demo.application.shared.command;

import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;

/**
 * 上傳檔案應用命令 (UploadFileCommand - Record)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>V + N + Command</code> (AGENTS.md 3-4)。</li>
 *   <li>代表應用層執行檔案上傳業務流程的不可變命令載體 (Immutable Command Record)。</li>
 *   <li>由表現層防腐轉換器 (<code>TransmissionResourceAssembler</code>) 將 HTTP 請求轉為此命令，
 *       傳遞給 <code>UploadFileUseCase</code> 進行傳輸調度。</li>
 * </ul>
 * </p>
 *
 * @param serverConfig 遠端伺服器連線與驗證配置
 * @param localFilePath 本機待上傳檔案路徑
 * @param remotePath 遠端目標儲存目錄路徑
 * @param filename 遠端目標儲存檔名 (若為 null 則沿用本機檔名)
 */
public record UploadFileCommand(
        TransmissionServerConfig serverConfig,
        LocalFilePath localFilePath,
        RemoteFilePath remotePath,
        String filename
) {

    /**
     * 便捷工廠方法：透過基本型別參數快速構建 UploadFileCommand
     *
     * @param host 主機位址
     * @param port 埠號
     * @param protocol 通訊協定 (FTP, FTPS, SFTP)
     * @param username 登入帳號
     * @param password 登入密碼
     * @param localPath 本機檔案路徑字串
     * @param remotePath 遠端目標目錄路徑字串
     * @param filename 目標檔名
     * @return UploadFileCommand 不可變命令物件
     */
    public static UploadFileCommand of(
            String host,
            int port,
            TransmissionProtocol protocol,
            String username,
            String password,
            String localPath,
            String remotePath,
            String filename
    ) {
        TransmissionServerConfig config = TransmissionServerConfig.of(host, port, protocol, username, password);
        return new UploadFileCommand(config, LocalFilePath.of(localPath), RemoteFilePath.of(remotePath), filename);
    }
}
