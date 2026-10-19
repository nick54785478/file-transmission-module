package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.UploadFileCommand;
import com.example.demo.application.shared.dto.FileUploadGottenResult;

/**
 * 上傳檔案使用案例介面 (UploadFileUseCase - Inbound Port)
 * <p>
 * 命名規範: in : ... + UseCase (3-8)
 * 定義將本機檔案上傳至遠端檔案伺服器 (FTP / FTPS / SFTP) 的業務合約。
 * </p>
 */
public interface UploadFileUseCase {

    /**
     * 執行檔案上傳作業
     *
     * @param command 上傳命令參數
     * @return 檔案上傳專屬結果 DTO (FileUploadGottenResult)
     */
    FileUploadGottenResult uploadFile(UploadFileCommand command);
}
