package com.example.demo.infrastructure.transmission.client.impl;

import com.example.demo.application.shared.exception.TransmissionConnectionException;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.util.TrustManagerUtils;
import org.springframework.stereotype.Component;

/**
 * FTPS (FTP over SSL/TLS) 安全加密傳輸協定客戶端實作 (FtpsTransmissionClient)
 * <p>
 * <b>架構與技術職責：</b>
 * <ul>
 *   <li>繼承自 {@link FtpTransmissionClient}，重複利用基礎 FTP 之檔案上傳、下載、遞迴同步與目錄維護邏輯。</li>
 *   <li>底層採用 Apache Commons Net 之 {@link FTPSClient}，建立基於 TLS 加密的傳輸控制與資料通道。</li>
 *   <li>支援 Explicit TLS (顯式加密) 連線模式，預設通訊埠為 21。</li>
 *   <li>強制設定保護緩衝區大小 (PBSZ 0) 與資料通道保護等級為 Private (PROT P)，確保二進位檔案資料傳輸全程加密傳送。</li>
 *   <li>所有底層連線異常均自動轉譯為統一的傳輸例外 {@link TransmissionConnectionException}。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class FtpsTransmissionClient extends FtpTransmissionClient {

    /**
     * 判定本客戶端元件是否支援指定的傳輸協定
     *
     * @param protocol 待檢核之傳輸協定列舉
     * @return 若協定為 {@link TransmissionProtocol#FTPS} 則回傳 {@code true}，否則回傳 {@code false}
     */
    @Override
    public boolean supports(TransmissionProtocol protocol) {
        return TransmissionProtocol.FTPS == protocol;
    }

    /**
     * 建立並初始化安全的 FTPSClient 連線實例
     * <p>
     * <b>連線初始化流程：</b>
     * <ol>
     *   <li>實例化 {@link FTPSClient}，指定通訊協定為 "TLS" 且停用隱式加密 (isImplicit = false)。</li>
     *   <li>設定字元編碼為 UTF-8，配置 Connect、Data 與 Default Timeout 逾時參數。</li>
     *   <li>設定 I/O 緩衝區大小為 1MB (1024 * 1024 bytes) 以提升大檔案傳輸效能。</li>
     *   <li>向遠端主機建立 TCP 連線，並檢核 FTPS 回應碼 (ReplyCode) 是否為肯定完成狀態 (Positive Completion)。</li>
     *   <li>傳送帳號與密碼執行身分驗證，登入失敗時主動斷開連線並拋出例外。</li>
     *   <li>執行 {@code execPBSZ(0)} 配置保護緩衝區大小為 0。</li>
     *   <li>執行 {@code execPROT("P")} 將資料通道 (Data Channel) 設定為最高加密等級 Private，防止檔案內容遭竊聽。</li>
     *   <li>依據配置切換被動模式 (Passive Mode) 或主動模式 (Active Mode)，並鎖定檔案傳輸型態為二進位 (Binary Mode)。</li>
     * </ol>
     * </p>
     *
     * @param config 傳輸伺服器連線配置模型，包含主機、連接埠、帳號、密碼與逾時設定
     * @return 已成功連線、通過 TLS 交握、完成身分認證且啟用資料加密的 {@link FTPClient} (實際為 {@link FTPSClient}) 實例
     * @throws IOException 當底層 Socket 網路連線中斷或 I/O 發生故障時拋出
     * @throws TransmissionConnectionException 當遠端伺服器拒絕連線、回應碼異常或帳號密碼登入失敗時拋出
     */
    @Override
    protected FTPClient createAndConnectClient(TransmissionServerConfig config) throws IOException {
        FTPSClient ftpsClient = new FTPSClient("TLS", false);
        // 配置信任自簽憑證 (Trust All Certificates)，以支援測試環境與自簽憑證 FTPS 伺服器
        ftpsClient.setTrustManager(TrustManagerUtils.getAcceptAllTrustManager());
        ftpsClient.setControlEncoding(StandardCharsets.UTF_8.name());
        ftpsClient.setConnectTimeout(config.getTimeoutMs());
        ftpsClient.setDataTimeout(config.getTimeoutMs());
        ftpsClient.setDefaultTimeout(config.getTimeoutMs());
        ftpsClient.setBufferSize(1024 * 1024);

        ftpsClient.connect(config.getHost(), config.getPort());

        int replyCode = ftpsClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(replyCode)) {
            ftpsClient.disconnect();
            throw new TransmissionConnectionException("FTPS 伺服器拒絕連線 (ReplyCode: " + replyCode + ")");
        }

        boolean loginSuccess = ftpsClient.login(config.getUsername(), config.getPassword());
        if (!loginSuccess) {
            ftpsClient.disconnect();
            throw new TransmissionConnectionException("FTPS 登入失敗: 帳號或密碼錯誤 (user=" + config.getUsername() + ")");
        }

        // 設置安全緩衝區大小 (PBSZ: Protection Buffer Size, 0 代表無限制)
        ftpsClient.execPBSZ(0);
        // 設置資料保護等級 (PROT: Data Channel Protection Level, P 代表 Private / Encrypted 加密保護)
        ftpsClient.execPROT("P");

        // 依據伺服器環境配置切換傳輸通道模式 (穿透 NAT/防火牆建議使用被動模式)
        if (config.isPassiveMode()) {
            ftpsClient.enterLocalPassiveMode();
        } else {
            ftpsClient.enterLocalActiveMode();
        }

        // 強制鎖定為二進位檔案傳輸模式，防止文字 ASCII 轉換損壞二進位檔案
        ftpsClient.setFileType(FTP.BINARY_FILE_TYPE);
        log.info("[FTPS] 連線、TLS 交握與身分驗證成功: {}:{} (PROT=P)", config.getHost(), config.getPort());
        return ftpsClient;
    }
}
