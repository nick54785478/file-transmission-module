package com.example.demo.application.shared.exception;

/**
 * 遠端伺服器連線與驗證異常 (TransmissionConnectionException)
 * <p>
 * <b>架構職責說明：</b>
 * <ul>
 *   <li>繼承自 {@link FileTransmissionException}，專門表示與遠端主機建立連線、TLS 握手、通訊協定協商或身份驗證時所發生的異常。</li>
 *   <li>在六角形架構中，基礎設施層的傳輸適配器 (Transmission Adapter) 與協定客戶端 (如 FTP、FTPS、SFTP Client)
 *       在捕獲底層連線逾時、網路中斷、認證失敗 (如密碼錯誤或金鑰無效) 時，應包裝或拋出此例外，
 *       使應用層 UseCase 能精準識別連線階段的失敗，並進行重試機制評估或對表現層提供具體錯誤資訊。</li>
 * </ul>
 * </p>
 */
public class TransmissionConnectionException extends FileTransmissionException {

    private static final long serialVersionUID = 1L;

    /**
     * 建構包含錯誤訊息的連線與驗證例外
     *
     * @param message 異常描述訊息 (如連線逾時或認證遭拒說明)
     */
    public TransmissionConnectionException(String message) {
        super(message);
    }

    /**
     * 建構包含錯誤訊息與根本原因 (Root Cause) 的連線與驗證例外
     *
     * @param message 異常描述訊息
     * @param cause 根本原因例外 (如底層 SocketTimeoutException、JSchException、IOException 等)
     */
    public TransmissionConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
