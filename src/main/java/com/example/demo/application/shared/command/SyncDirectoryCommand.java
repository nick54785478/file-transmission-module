package com.example.demo.application.shared.command;

import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;

/**
 * 目錄同步應用命令 (SyncDirectoryCommand - Record)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>V + N + Command</code> (AGENTS.md 3-4)。</li>
 *   <li>代表應用層執行本機與遠端目錄同步業務流程的不可變命令載體 (Immutable Command Record)。</li>
 *   <li>由表現層防腐轉換器 (<code>TransmissionResourceAssembler</code>) 將 HTTP 請求轉為此命令，
 *       傳遞給 <code>SyncDirectoryUseCase</code> 進行目錄同步排程或即時同步處理。</li>
 * </ul>
 * </p>
 *
 * @param serverConfig 遠端伺服器連線與驗證配置
 * @param localDir 本機目錄路徑
 * @param remoteDir 遠端目錄路徑
 * @param direction 同步方向 (LOCAL_TO_REMOTE, REMOTE_TO_LOCAL, BIDIRECTIONAL)
 * @param deleteRemoteExtras 是否在同步後刪除目標端多餘檔案
 */
public record SyncDirectoryCommand(
        TransmissionServerConfig serverConfig,
        LocalFilePath localDir,
        RemoteFilePath remoteDir,
        SyncDirection direction,
        boolean deleteRemoteExtras
) {

    /**
     * 便捷工廠方法：透過基本型別參數快速構建 SyncDirectoryCommand
     *
     * @param host 主機位址
     * @param port 埠號
     * @param protocol 通訊協定 (FTP, FTPS, SFTP)
     * @param username 登入帳號
     * @param password 登入密碼
     * @param localDir 本機目錄路徑字串
     * @param remoteDir 遠端目錄路徑字串
     * @param direction 同步方向
     * @param deleteRemoteExtras 是否刪除多餘檔案
     * @return SyncDirectoryCommand 不可變命令物件
     */
    public static SyncDirectoryCommand of(
            String host,
            int port,
            TransmissionProtocol protocol,
            String username,
            String password,
            String localDir,
            String remoteDir,
            SyncDirection direction,
            boolean deleteRemoteExtras
    ) {
        TransmissionServerConfig config = TransmissionServerConfig.of(host, port, protocol, username, password);
        return new SyncDirectoryCommand(
                config,
                LocalFilePath.of(localDir),
                RemoteFilePath.of(remoteDir),
                direction,
                deleteRemoteExtras
        );
    }
}
