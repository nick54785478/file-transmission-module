package com.example.demo.application.port.in;

import com.example.demo.application.shared.command.SyncDirectoryCommand;
import com.example.demo.application.shared.dto.DirectorySyncGottenResult;

/**
 * 目錄同步使用案例介面 (SyncDirectoryUseCase - Inbound Port)
 * <p>
 * 命名規範: in : ... + UseCase (3-8)
 * 定義本機與遠端目錄之間進行雙向/單向檔案同步的業務合約。
 * </p>
 */
public interface SyncDirectoryUseCase {

    /**
     * 執行目錄同步作業
     *
     * @param command 同步命令參數
     * @return 同步結果 DTO
     */
    DirectorySyncGottenResult syncDirectory(SyncDirectoryCommand command);
}
