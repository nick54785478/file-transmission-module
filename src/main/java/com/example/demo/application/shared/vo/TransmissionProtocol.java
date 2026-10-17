package com.example.demo.application.shared.vo;

/**
 * 檔案傳輸協定類型 (TransmissionProtocol - Value Object / Enum)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>代表應用層與輸出埠 (FileTransmissionGatewayPort) 支援的遠端傳輸通訊協定列舉數值物件 (FTP, FTPS, SFTP)。</li>
 *   <li>作為連線配置 (TransmissionServerConfig) 的核心組成，提供各傳輸協定標準預設埠號 (Default Port)。</li>
 * </ul>
 * </p>
 */
public enum TransmissionProtocol {

    /**
     * 標準 FTP 協定 (File Transfer Protocol，明文傳輸)
     * 預設埠號: 21
     */
    FTP(21),

    /**
     * FTPS 協定 (FTP over SSL/TLS，安全加密傳輸)
     * 預設埠號: 21 (Explicit) 或 990 (Implicit)
     */
    FTPS(21),

    /**
     * SFTP 協定 (SSH File Transfer Protocol，基於 SSH 協定之傳輸)
     * 預設埠號: 22
     */
    SFTP(22);

    private final int defaultPort;

    TransmissionProtocol(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    /**
     * 取得該協定的預設連接埠號
     *
     * @return 預設連接埠號
     */
    public int getDefaultPort() {
        return defaultPort;
    }
}
