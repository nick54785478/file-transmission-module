package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.UploadFileFromMemoryCommand;
import com.example.demo.application.shared.dto.FileUploadGottenResult;

/**
 * 上傳記憶體檔案使用案例介面 (UploadFileFromMemoryUseCase - Inbound Port)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>in : ... + UseCase</code> (AGENTS.md 3-8)。</li>
 *   <li>定義將記憶體內的二進位位元組資料直接傳輸至遠端檔案伺服器 (FTP / FTPS / SFTP) 的業務合約。</li>
 *   <li>支援零磁碟落盤 (Zero Disk I/O)，完全避免本機暫存檔產生與磁碟 I/O 開銷。</li>
 * </ul>
 * </p>
 */
public interface UploadFileFromMemoryUseCase {

    /**
     * 執行記憶體檔案上傳作業 (Zero Disk I/O)
     *
     * @param command 記憶體檔案上傳命令參數
     * @return 檔案上傳專屬結果 DTO (FileUploadGottenResult)
     */
    FileUploadGottenResult uploadFileFromMemory(UploadFileFromMemoryCommand command);
}
