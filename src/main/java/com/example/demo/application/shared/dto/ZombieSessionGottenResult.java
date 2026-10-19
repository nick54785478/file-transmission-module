package com.example.demo.application.shared.dto;

import com.example.demo.application.shared.vo.TransmissionStage;
import java.io.Serializable;
import java.time.Instant;

/**
 * 停滯殭屍傳輸作業資料載體 (ZombieSessionGottenResult - DTO Record)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>N + Gotten + Result</code> (AGENTS.md 3-6)。</li>
 *   <li>代表超時停滯於 IN_PROGRESS 狀態之作業階段資訊，供背景對帳引擎執行狀態探測與自癒修復。</li>
 * </ul>
 * </p>
 *
 * @param trackingId 傳輸作業追蹤唯一識別碼
 * @param protocol 通訊協定代碼 (FTP / FTPS / SFTP)
 * @param status 當前狀態 (IN_PROGRESS)
 * @param stage 當前作業細部階段 (STAGING, VERIFYING, COMMITTING)
 * @param sourcePath 來源路徑
 * @param targetPath 目標路徑
 * @param fileName 檔案名稱
 * @param stagingPath 遠端暫存路徑
 * @param fileSizeBytes 預期檔案大小
 * @param serverHost 遠端主機 IP 或域名
 * @param serverPort 遠端主機通訊埠
 * @param serverUsername 遠端服務帳號
 * @param serverPassword 遠端服務密碼
 * @param credentialId 關聯之憑證 ID
 * @param startedAt 作業開始時間戳記
 * @param version 樂觀鎖版本號
 */
public record ZombieSessionGottenResult(
        String trackingId,
        String protocol,
        String status,
        TransmissionStage stage,
        String sourcePath,
        String targetPath,
        String fileName,
        String stagingPath,
        Long fileSizeBytes,
        String serverHost,
        Integer serverPort,
        String serverUsername,
        String serverPassword,
        String credentialId,
        Instant startedAt,
        Long version
) implements Serializable {

    private static final long serialVersionUID = 1L;
}
