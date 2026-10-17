package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.SyncDirectoryCommand;
import com.example.demo.application.shared.dto.FileTransmissionSubmittedResult;

/**
 * 提交非同步目錄同步使用案例介面 (SubmitSyncDirectoryUseCase - Inbound Port)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>in : ... + UseCase</code> (AGENTS.md 3-8)。</li>
 *   <li>接收 {@link SyncDirectoryCommand}，立即派發至背景執行緒執行目錄雙向同步與狀態追蹤。</li>
 *   <li>毫秒級立即回傳包含 trackingId 之 {@link FileTransmissionSubmittedResult} 提交結果。</li>
 * </ul>
 * </p>
 */
public interface SubmitSyncDirectoryUseCase {

    /**
     * 提交非同步目錄同步任務
     *
     * @param command 目錄同步命令
     * @return 非同步任務提交結果 DTO
     */
    FileTransmissionSubmittedResult submitSyncDirectory(SyncDirectoryCommand command);
}
