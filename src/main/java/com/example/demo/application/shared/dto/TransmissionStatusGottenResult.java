package com.example.demo.application.shared.dto;

import java.io.Serializable;

/**
 * 傳輸任務狀態查詢結果資料傳輸物件 (TransmissionStatusGottenResult - Application DTO)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>N + Gotten + Result</code> (AGENTS.md 3-6)。</li>
 *   <li>代表依據 trackingId 查詢傳輸作業狀態與詳細審計指標之專屬唯讀資料傳輸物件 (DTO Record)。</li>
 *   <li><b>純資料載體守則：</b> 本類別嚴格作為純資料載體 (Pure Data Carrier)，內部禁止包含任何轉換邏輯；
 *       模型與 DTO 的映射轉換由 <code>application/assembler/TransmissionDtoAssembler</code> 統一負責。</li>
 * </ul>
 * </p>
 *
 * @param transmissionId 傳輸作業唯一識別碼 (UUID)
 * @param protocol 使用的通訊協定名稱 (FTP, FTPS, SFTP)
 * @param status 傳輸狀態 (PENDING, IN_PROGRESS, COMPLETED, FAILED)
 * @param sourcePath 來源檔案或目錄完整路徑
 * @param targetPath 目標儲存路徑
 * @param fileName 傳輸檔案名稱
 * @param fileSizeBytes 傳輸檔案大小 (bytes)
 * @param durationMillis 傳輸耗時 (毫秒)
 * @param errorMessage 錯誤描述訊息 (若無異常則為 null)
 */
public record TransmissionStatusGottenResult(
        String transmissionId,
        String protocol,
        String status,
        String sourcePath,
        String targetPath,
        String fileName,
        long fileSizeBytes,
        long durationMillis,
        String errorMessage
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
