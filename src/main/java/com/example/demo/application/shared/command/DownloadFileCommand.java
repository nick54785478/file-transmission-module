package com.example.demo.application.shared.command;

import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;

/**
 * 下載檔案應用命令 (DownloadFileCommand - Record)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>V + N + Command</code> (AGENTS.md 3-4)。</li>
 *   <li>代表應用層執行遠端檔案下載業務流程的不可變命令載體 (Immutable Command Record)。</li>
 *   <li>由表現層防腐轉換器 (<code>TransmissionResourceAssembler</code>) 將 HTTP 請求轉為此命令，
 *       傳遞給 <code>DownloadFileUseCase</code> 進行傳輸調度。</li>
 * </ul>
 * </p>
 *
 * @param serverConfig 遠端伺服器連線與驗證配置
 * @param remoteFilePath 欲下載的遠端檔案完整路徑
 * @param savedPath 本機儲存目標路徑
 */
public record DownloadFileCommand(
        TransmissionServerConfig serverConfig,
        RemoteFilePath remoteFilePath,
        LocalFilePath savedPath
) {

    /**
     * 便捷工廠方法：透過基本型別參數快速構建 DownloadFileCommand
     *
     * @param host 主機位址
     * @param port 埠號
     * @param protocol 通訊協定 (FTP, FTPS, SFTP)
     * @param username 登入帳號
     * @param password 登入密碼
     * @param remoteFilePath 遠端檔案完整路徑字串
     * @param savedPath 本地儲存路徑字串
     * @return DownloadFileCommand 不可變命令物件
     */
    public static DownloadFileCommand of(
            String host,
            int port,
            TransmissionProtocol protocol,
            String username,
            String password,
            String remoteFilePath,
            String savedPath
    ) {
        TransmissionServerConfig config = TransmissionServerConfig.of(host, port, protocol, username, password);
        return new DownloadFileCommand(config, RemoteFilePath.of(remoteFilePath), LocalFilePath.of(savedPath));
    }
}
