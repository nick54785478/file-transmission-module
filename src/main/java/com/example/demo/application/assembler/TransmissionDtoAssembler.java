package com.example.demo.application.assembler;

import com.example.demo.application.shared.dto.DirectorySyncGottenResult;
import com.example.demo.application.shared.dto.FileDownloadGottenResult;
import com.example.demo.application.shared.dto.FileTransmissionGottenResult;
import com.example.demo.application.shared.dto.FileTransmissionSubmittedResult;
import com.example.demo.application.shared.dto.FileUploadGottenResult;
import com.example.demo.application.shared.dto.TransmissionStatusGottenResult;
import com.example.demo.application.shared.view.RemoteFileGottenView;
import com.example.demo.application.shared.vo.LocalFilePath;
import com.example.demo.application.shared.vo.RemoteFileInfo;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.SyncDirection;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 傳輸應用層組裝器 (TransmissionDtoAssembler)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>遵循六角形架構職責分離原則，DTO 與 View 保持為純資料載體 (Pure Data Carrier)。</li>
 *   <li>所有數值、實質物件 (Value Object) 與 DTO/View 之間的組裝與轉換邏輯統一於此 Assembler 實現。</li>
 *   <li><b>純淨應用層守則：</b> 嚴禁引入任何技術框架或 infrastructure 基礎設施實體類別，保證應用層依賴方向的單向純淨。</li>
 * </ul>
 * </p>
 */
public class TransmissionDtoAssembler {

    /**
     * 組裝檔案上傳結果資料傳輸物件 (FileUploadGottenResult)
     *
     * @param transmissionId 傳輸作業唯一識別碼
     * @param protocol 通訊協定名稱字串
     * @param status 傳輸狀態字串
     * @param sourcePath 本機來源檔案路徑
     * @param targetPath 遠端目標目錄路徑
     * @param fileName 上傳檔案名稱
     * @param fileSize 檔案大小位元組
     * @param durationMillis 傳輸耗時 (毫秒)
     * @param errorMessage 錯誤描述訊息 (若成功則為 null)
     * @return FileUploadGottenResult
     */
    public static FileUploadGottenResult toUploadResult(
            String transmissionId,
            String protocol,
            String status,
            LocalFilePath sourcePath,
            RemoteFilePath targetPath,
            String fileName,
            long fileSize,
            long durationMillis,
            String errorMessage
    ) {
        return new FileUploadGottenResult(
                transmissionId,
                protocol,
                status,
                sourcePath != null ? sourcePath.getValue() : "<memory>",
                targetPath != null ? targetPath.getValue() : "",
                fileName,
                fileSize,
                durationMillis,
                errorMessage
        );
    }

    /**
     * 組裝檔案下載結果資料傳輸物件 (FileDownloadGottenResult)
     *
     * @param transmissionId 傳輸作業唯一識別碼
     * @param protocol 通訊協定名稱字串
     * @param status 傳輸狀態字串
     * @param sourcePath 遠端來源檔案路徑
     * @param targetPath 本機目標目錄路徑
     * @param fileName 下載檔案名稱
     * @param fileSize 檔案大小位元組
     * @param durationMillis 傳輸耗時 (毫秒)
     * @param errorMessage 錯誤描述訊息 (若成功則為 null)
     * @return FileDownloadGottenResult
     */
    public static FileDownloadGottenResult toDownloadResult(
            String transmissionId,
            String protocol,
            String status,
            RemoteFilePath sourcePath,
            LocalFilePath targetPath,
            String fileName,
            long fileSize,
            long durationMillis,
            String errorMessage
    ) {
        return new FileDownloadGottenResult(
                transmissionId,
                protocol,
                status,
                sourcePath != null ? sourcePath.getValue() : "",
                targetPath != null ? targetPath.getValue() : "",
                fileName,
                fileSize,
                durationMillis,
                errorMessage
        );
    }

    /**
     * 組裝目錄同步結果資料傳輸物件 (DirectorySyncGottenResult)
     *
     * @param transmissionId 傳輸作業唯一識別碼
     * @param protocol 通訊協定名稱字串
     * @param status 傳輸狀態字串
     * @param direction 同步方向
     * @param localDir 本機目錄路徑
     * @param remoteDir 遠端目錄路徑
     * @param transferredCount 成功傳輸檔案總數
     * @param deletedCount 刪除的多餘檔案總數
     * @param durationMillis 同步耗時 (毫秒)
     * @param errorMessage 錯誤描述訊息 (若成功則為 null)
     * @return DirectorySyncGottenResult
     */
    public static DirectorySyncGottenResult toSyncResult(
            String transmissionId,
            String protocol,
            String status,
            SyncDirection direction,
            LocalFilePath localDir,
            RemoteFilePath remoteDir,
            int transferredCount,
            int deletedCount,
            long durationMillis,
            String errorMessage
    ) {
        return new DirectorySyncGottenResult(
                transmissionId,
                protocol,
                status,
                direction != null ? direction.name() : "",
                localDir != null ? localDir.getValue() : "",
                remoteDir != null ? remoteDir.getValue() : "",
                transferredCount,
                deletedCount,
                durationMillis,
                errorMessage
        );
    }

    /**
     * 組裝傳輸狀態查詢結果資料傳輸物件 (TransmissionStatusGottenResult)
     *
     * @param transmissionId 傳輸作業識別碼
     * @param protocol 通訊協定
     * @param status 傳輸狀態
     * @param sourcePath 來源路徑字串
     * @param targetPath 目標路徑字串
     * @param fileName 檔案名稱或操作描述
     * @param fileSize 檔案大小位元組
     * @param durationMillis 耗時毫秒
     * @param errorMessage 錯誤訊息
     * @return TransmissionStatusGottenResult
     */
    public static TransmissionStatusGottenResult toStatusResult(
            String transmissionId,
            String protocol,
            String status,
            String sourcePath,
            String targetPath,
            String fileName,
            long fileSize,
            long durationMillis,
            String errorMessage
    ) {
        return new TransmissionStatusGottenResult(
                transmissionId,
                protocol,
                status,
                sourcePath != null ? sourcePath : "",
                targetPath != null ? targetPath : "",
                fileName != null ? fileName : "",
                fileSize,
                durationMillis,
                errorMessage
        );
    }

    /**
     * 組裝通用傳輸狀態查詢結果 (相容舊呼叫)
     *
     * @param transmissionId 傳輸作業識別碼
     * @param protocol 通訊協定
     * @param status 傳輸狀態
     * @param sourcePath 來源路徑字串
     * @param targetPath 目標路徑字串
     * @param fileName 檔案名稱或操作描述
     * @param fileSize 檔案大小位元組
     * @param durationMillis 耗時毫秒
     * @param errorMessage 錯誤訊息
     * @return FileTransmissionGottenResult
     */
    @Deprecated
    public static FileTransmissionGottenResult toGottenResult(
            String transmissionId,
            String protocol,
            String status,
            String sourcePath,
            String targetPath,
            String fileName,
            long fileSize,
            long durationMillis,
            String errorMessage
    ) {
        return new FileTransmissionGottenResult(
                transmissionId,
                protocol,
                status,
                sourcePath != null ? sourcePath : "",
                targetPath != null ? targetPath : "",
                fileName != null ? fileName : "",
                fileSize,
                durationMillis,
                errorMessage
        );
    }

    /**
     * 組裝非同步任務提交結果資料載體 (FileTransmissionSubmittedResult)
     *
     * @param trackingId 追蹤唯一識別碼
     * @param protocol 傳輸協定名稱
     * @param status 狀態字串 (如 IN_PROGRESS)
     * @param message 提示訊息
     * @return FileTransmissionSubmittedResult
     */
    public static FileTransmissionSubmittedResult toSubmittedResult(
            String trackingId,
            String protocol,
            String status,
            String message
    ) {
        return new FileTransmissionSubmittedResult(
                trackingId,
                protocol,
                status,
                message,
                Instant.now()
        );
    }

    /**
     * 將 RemoteFileInfo 領域物件轉換為 RemoteFileGottenView 投影
     *
     * @param info 遠端檔案中繼資料領域物件
     * @return RemoteFileGottenView 投影物件
     */
    public static RemoteFileGottenView toView(RemoteFileInfo info) {
        if (info == null) {
            return null;
        }
        return new RemoteFileGottenView(
                info.getFileName(),
                info.getFullPath().getValue(),
                info.getSize(),
                info.getLastModifiedMillis(),
                info.isDirectory()
        );
    }

    /**
     * 批次轉換 RemoteFileInfo 清單為 RemoteFileGottenView 清單
     *
     * @param infoList 遠端檔案中繼資料清單
     * @return 投影檢視清單
     */
    public static List<RemoteFileGottenView> toViewList(List<RemoteFileInfo> infoList) {
        if (infoList == null || infoList.isEmpty()) {
            return Collections.emptyList();
        }
        return infoList.stream()
                .map(TransmissionDtoAssembler::toView)
                .collect(Collectors.toList());
    }
}
