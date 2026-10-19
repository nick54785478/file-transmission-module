package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.DownloadFileCommand;
import com.example.demo.application.shared.dto.FileDownloadGottenResult;

/**
 * 下載檔案使用案例介面 (DownloadFileUseCase - Inbound Port)
 * <p>
 * 命名規範: in : ... + UseCase (3-8)
 * 定義從遠端檔案伺服器 (FTP / FTPS / SFTP) 下載檔案至本機的業務合約。
 * </p>
 */
public interface DownloadFileUseCase {

    /**
     * 執行檔案下載作業
     *
     * @param command 下載命令參數
     * @return 檔案下載專屬結果 DTO (FileDownloadGottenResult)
     */
    FileDownloadGottenResult downloadFile(DownloadFileCommand command);
}
