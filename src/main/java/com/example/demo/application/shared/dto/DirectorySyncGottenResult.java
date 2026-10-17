package com.example.demo.application.shared.dto;

import java.io.Serializable;

/**
 * 目錄同步執行結果資料載體 (DirectorySyncGottenResult - DTO Record)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>N + Gotten + Result</code> (AGENTS.md 3-6)。</li>
 *   <li>代表目錄同步完成後的唯讀結果資料傳輸物件 (DTO Record)。</li>
 *   <li><b>純資料載體守則：</b> 本類別嚴格作為純資料載體 (Pure Data Carrier)，內部禁止包含任何轉換邏輯；
 *       模型與 DTO 的映射轉換由 <code>application/assembler/TransmissionDtoAssembler</code> 統一負責。</li>
 * </ul>
 * </p>
 *
 * @param transmissionId 傳輸作業唯一識別碼 (UUID)
 * @param protocol 使用的通訊協定名稱 (FTP, FTPS, SFTP)
 * @param status 傳輸狀態 (如 COMPLETED, FAILED)
 * @param direction 同步方向 (LOCAL_TO_REMOTE, REMOTE_TO_LOCAL, BIDIRECTIONAL)
 * @param localDir 本機目錄路徑
 * @param remoteDir 遠端目錄路徑
 * @param filesTransferredCount 成功傳輸檔案總數
 * @param filesDeletedCount 刪除的多餘檔案總數
 * @param durationMillis 同步耗時 (毫秒)
 * @param errorMessage 錯誤描述訊息 (若成功則為 null)
 */
public record DirectorySyncGottenResult(
        String transmissionId,
        String protocol,
        String status,
        String direction,
        String localDir,
        String remoteDir,
        int filesTransferredCount,
        int filesDeletedCount,
        long durationMillis,
        String errorMessage
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
