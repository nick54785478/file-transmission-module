package com.example.demo.application.shared.exception;

/**
 * 檔案傳輸應用層基底例外 (FileTransmissionException)
 * <p>
 * <b>架構職責說明：</b>
 * <ul>
 *   <li>作為檔案傳輸應用層 (Application Layer) 所有傳輸作業失敗、I/O 異常或業務規則檢核錯誤的頂層通用未檢查例外 (Unchecked RuntimeException)。</li>
 *   <li>在六角形架構中，輸出埠 (Outbound Port) 與協定客戶端 (Protocol Client) 拋出或轉譯為此例外，由應用層 UseCase 向外傳遞至表現層 (Presentation Layer) 進行統一錯誤處理或轉化為 HTTP 錯誤回應。</li>
 * </ul>
 * </p>
 */
public class FileTransmissionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 建構包含錯誤訊息的檔案傳輸例外
     *
     * @param message 異常描述訊息
     */
    public FileTransmissionException(String message) {
        super(message);
    }

    /**
     * 建構包含錯誤訊息與根本原因 (Root Cause) 的檔案傳輸例外
     *
     * @param message 異常描述訊息
     * @param cause 根本原因例外 (如底層 IOException、SftpException 等)
     */
    public FileTransmissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
