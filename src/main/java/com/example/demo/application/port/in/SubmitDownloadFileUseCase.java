package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.DownloadFileCommand;
import com.example.demo.application.shared.dto.FileTransmissionSubmittedResult;

/**
 * 提交非同步檔案下載使用案例介面 (SubmitDownloadFileUseCase - Inbound Port)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>in : ... + UseCase</code> (AGENTS.md 3-8)。</li>
 *   <li>接收 {@link DownloadFileCommand}，立即派發至背景執行緒執行檔案下載與狀態追蹤。</li>
 *   <li>毫秒級立即回傳包含 trackingId 之 {@link FileTransmissionSubmittedResult} 提交結果。</li>
 * </ul>
 * </p>
 */
public interface SubmitDownloadFileUseCase {

    /**
     * 提交非同步檔案下載任務
     *
     * @param command 檔案下載命令
     * @return 非同步任務提交結果 DTO
     */
    FileTransmissionSubmittedResult submitDownloadFile(DownloadFileCommand command);
}
