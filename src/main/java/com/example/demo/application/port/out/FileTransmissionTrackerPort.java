package com.example.demo.application.port.out;

import com.example.demo.application.shared.dto.DirectorySyncGottenResult;
import com.example.demo.application.shared.dto.FileDownloadGottenResult;
import com.example.demo.application.shared.dto.FileUploadGottenResult;
import com.example.demo.application.shared.dto.TransmissionStatusGottenResult;
import com.example.demo.application.shared.dto.ZombieSessionGottenResult;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 檔案傳輸追蹤輸出埠介面 (FileTransmissionTrackerPort - Outbound Port)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>out : ... + Port</code> (AGENTS.md 3-8)。</li>
 *   <li>六角形架構 Outbound Port，代表應用層對外要求基礎設施層提供的檔案傳輸生命週期追蹤 (Tracker) 合約。</li>
 *   <li>專職負責追蹤單次傳輸作業的全程生命週期：啟動追蹤、紀錄成功狀態、計算耗時指標、捕捉失敗錯誤以及維護追蹤實體 (TransmissionSession Entity)。</li>
 *   <li>使 Application Service (如 FileTransmissionCommandService) 能專注於 Use Case 流程調度，達到零基礎設施依賴。</li>
 *   <li><b>純淨規範守則：</b> 本介面方法之傳參及回傳型別嚴禁使用任何技術類或基礎設施實體物件，僅可使用純領域實質物件 (Value Objects) 與純資料載體 (DTOs)。</li>
 * </ul>
 * </p>
 */
public interface FileTransmissionTrackerPort {

    /**
     * 啟動傳輸作業追蹤並返回追蹤唯一識別碼
     *
     * @param serverConfig 伺服器連線配置實質物件
     * @return 追蹤識別碼字串 (Tracking ID / Transmission ID)
     */
    String startTracking(TransmissionServerConfig serverConfig);

    /**
     * 記錄進入 2PC 遠端暫存寫入階段 (STAGING)
     *
     * @param trackingId 傳輸追蹤唯一識別碼
     * @param stagingPath 遠端暫存檔案完整路徑
     */
    void recordStaging(String trackingId, String stagingPath);

    /**
     * 記錄進入完整性與大小檢核階段 (VERIFYING)
     *
     * @param trackingId 傳輸追蹤唯一識別碼
     */
    void recordVerifying(String trackingId);

    /**
     * 記錄進入原子更名提交階段 (COMMITTING)
     *
     * @param trackingId 傳輸追蹤唯一識別碼
     */
    void recordCommitting(String trackingId);

    /**
     * 記錄檔案上傳成功並產生對應的專屬上傳結果資料載體
     *
     * @param trackingId 傳輸追蹤唯一識別碼
     * @param sourcePath 本機來源檔案路徑
     * @param targetPath 遠端目標目錄路徑
     * @param fileName 上傳檔案名稱
     * @param fileSizeBytes 上傳檔案大小 (bytes)
     * @return FileUploadGottenResult 檔案上傳完成結果資料傳輸物件
     */
    FileUploadGottenResult recordUploadSuccess(
            String trackingId,
            LocalFilePath sourcePath,
            RemoteFilePath targetPath,
            String fileName,
            long fileSizeBytes
    );

    /**
     * 記錄檔案下載成功並產生對應的專屬下載結果資料載體
     *
     * @param trackingId 傳輸追蹤唯一識別碼
     * @param sourcePath 遠端來源檔案路徑
     * @param targetPath 本機目標目錄路徑
     * @param fileName 下載檔案名稱
     * @param fileSizeBytes 下載檔案大小 (bytes)
     * @return FileDownloadGottenResult 檔案下載完成結果資料傳輸物件
     */
    FileDownloadGottenResult recordDownloadSuccess(
            String trackingId,
            RemoteFilePath sourcePath,
            LocalFilePath targetPath,
            String fileName,
            long fileSizeBytes
    );

    /**
     * 記錄目錄同步成功並產生對應的目錄同步結果資料載體
     *
     * @param trackingId 傳輸追蹤唯一識別碼
     * @param direction 目錄同步傳輸方向
     * @param localDir 本機目錄路徑
     * @param remoteDir 遠端目錄路徑
     * @param transferredCount 成功傳輸檔案總數
     * @param deletedCount 刪除的多餘檔案總數
     * @return DirectorySyncGottenResult 目錄同步完成結果資料傳輸物件
     */
    DirectorySyncGottenResult recordSyncSuccess(
            String trackingId,
            SyncDirection direction,
            LocalFilePath localDir,
            RemoteFilePath remoteDir,
            int transferredCount,
            int deletedCount
    );

    /**
     * 記錄傳輸作業追蹤失敗
     *
     * @param trackingId 傳輸追蹤唯一識別碼
     * @param errorMessage 錯誤描述訊息
     */
    void recordFailure(String trackingId, String errorMessage);

    /**
     * 依追蹤唯一識別碼查詢傳輸作業階段狀態與資料載體
     *
     * @param trackingId 傳輸追蹤唯一識別碼
     * @return 傳輸階段結果物件 Optional (若查無資料則為 empty)
     */
    Optional<TransmissionStatusGottenResult> getTransmissionStatus(String trackingId);

    /**
     * 查詢所有停滯超過指定閾值之殭屍任務 (供背景對帳自癒排程使用)
     *
     * @param threshold 停滯時間閾值 (例如 Duration.ofMinutes(30))
     * @return 殭屍任務結果清單
     */
    List<ZombieSessionGottenResult> findZombieSessions(Duration threshold);

    /**
     * 記錄對帳自癒成功 (遠端檔案確認已存在完整)
     *
     * @param trackingId 傳輸追蹤唯一識別碼
     * @param fileSizeBytes 遠端檔案大小
     */
    void recordReconciliationSuccess(String trackingId, long fileSizeBytes);

    /**
     * 記錄對帳清理失敗或逾時終止 (清理殘留臨時檔並標記失敗)
     *
     * @param trackingId 傳輸追蹤唯一識別碼
     * @param errorMessage 失敗說明
     */
    void recordReconciliationFailure(String trackingId, String errorMessage);
}

