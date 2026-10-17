package com.example.demo.application.shared.exception;

/**
 * 遠端目錄建立失敗異常 (RemoteDirectoryCreationException)
 * <p>
 * <b>架構職責說明：</b>
 * <ul>
 *   <li>繼承自 {@link FileTransmissionException}，專門表示在遠端伺服器上遞迴或單層建立工作目錄失敗時發生的異常。</li>
 *   <li>在六角形架構中，基礎設施層的傳輸適配器 (Transmission Adapter) 與協定客戶端 (如 FTP、FTPS、SFTP Client)
 *       在執行檔案上傳或目錄同步前，若偵測到遠端權限不足 (Permission Denied)、路徑非法或檔案系統唯讀等情況而無法建立目標資料夾時拋出，
 *       使應用層 UseCase 能精確區分是目錄準備失敗而非檔案傳輸本身的中斷。</li>
 * </ul>
 * </p>
 */
public class RemoteDirectoryCreationException extends FileTransmissionException {

    private static final long serialVersionUID = 1L;

    /**
     * 建構包含錯誤訊息的遠端目錄建立例外
     *
     * @param message 異常描述訊息 (如遠端目標路徑建立失敗或權限不足說明)
     */
    public RemoteDirectoryCreationException(String message) {
        super(message);
    }

    /**
     * 建構包含錯誤訊息與根本原因 (Root Cause) 的遠端目錄建立例外
     *
     * @param message 異常描述訊息
     * @param cause 根本原因例外 (如底層 SftpException、FTP 協定回應代碼異常或 I/O 錯誤等)
     */
    public RemoteDirectoryCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
