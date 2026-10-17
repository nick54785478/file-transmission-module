package com.example.demo.infrastructure.transmission.client.impl;

import com.example.demo.application.shared.exception.TransmissionConnectionException;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
        // 比對傳輸協定列舉，本元件專責處理 FTPS (FTP over SSL/TLS) 安全加密連線
        return TransmissionProtocol.FTPS == protocol;
    }

    /**
     * 建立並初始化安全的 FTPSClient 連線實例
     * <p>
     * <b>連線初始化流程：</b>
     * <ol>
     *   <li>實例化 {@link FTPSClient}，指定通訊協定為 "TLS" 且採用顯式加密 (Explicit FTPS, isImplicit = false)。</li>
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
        // 步驟 1：建立 FTPSClient 實例
        // - 第一個參數 "TLS"：指定使用標準 TLS 加密協定交握 (相容 TLSv1.2 / TLSv1.3)
        // - 第二個參數 isImplicit = false：使用顯式加密 (Explicit TLS / FTPES)，預設監聽 Port 21。
        //   連線建立初期先走標準 FTP 控制通道，隨後以 AUTH TLS 指令升級為安全 TLS 通道。
        //   (若為隱式加密 isImplicit=true，通常監聽 Port 990，連線初始即需 SSL/TLS 交握，已非 RFC 4217 標準推薦模式)
        FTPSClient ftpsClient = new FTPSClient("TLS", false);

        // 步驟 2：配置信任管理器 (TrustManager)
        // 使用 AcceptAllTrustManager 信任所有自簽或內部 CA 憑證，適用於企業內部私有 FTPS 或測試環境。
        // [維運注意] 若生產環境需嚴格校驗合法 CA 憑證鏈，可在此抽換為驗證指定 KeyStore/TrustStore 的 TrustManager。
        ftpsClient.setTrustManager(TrustManagerUtils.getAcceptAllTrustManager());

        // 步驟 3：配置編碼、逾時參數與緩衝區大小
        // 強制控制通道使用 UTF-8 編碼，防止包含中文或特殊字符之檔名與路徑在命令解析時出現亂碼
        ftpsClient.setControlEncoding(StandardCharsets.UTF_8.name());

        // 設定 TCP Socket 連線逾時 (毫秒)
        ftpsClient.setConnectTimeout(config.getTimeoutMs());

        // 設定資料通道 (Data Channel) 讀取與傳輸 Socket 逾時 (使用 Duration 避免過時方法警告)，防止網路中斷時執行緒無限等待
        ftpsClient.setDataTimeout(Duration.ofMillis(config.getTimeoutMs()));

        // 設定控制通道 (Control Channel) 指令與回應接收之預設逾時 (毫秒)
        ftpsClient.setDefaultTimeout(config.getTimeoutMs());

        // 將底層 I/O 緩衝區擴增為 1MB (1024 * 1024 bytes)，顯著減少底層 Socket 讀寫次數以提高大檔案吞吐量
        ftpsClient.setBufferSize(1024 * 1024);

        // 步驟 4：向遠端伺服器建立 TCP 連線
        ftpsClient.connect(config.getHost(), config.getPort());

        // 步驟 5：校驗伺服器初始回應碼 (ReplyCode)
        // 正確狀態通常為 220 (Service ready for new user)。若非 Positive Completion (2xx)，代表伺服器拒絕連線
        int replyCode = ftpsClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(replyCode)) {
            // 連線失敗時必須主動關閉 Socket 連線釋放系統資源
            ftpsClient.disconnect();
            throw new TransmissionConnectionException("FTPS 伺服器拒絕連線 (ReplyCode: " + replyCode + ")");
        }

        // 步驟 6：傳送帳號與密碼執行身分認證 (LOGIN)
        boolean loginSuccess = ftpsClient.login(config.getUsername(), config.getPassword());
        if (!loginSuccess) {
            // 登入失敗時主動斷開連線，防止在伺服器端殘留未認證的半開連線 (Half-open connection)
            ftpsClient.disconnect();
            throw new TransmissionConnectionException("FTPS 登入失敗: 帳號或密碼錯誤 (user=" + config.getUsername() + ")");
        }

        // 步驟 7：配置 RFC 4217 安全傳輸參數 (PBSZ 與 PROT)
        // - execPBSZ(0)：Protection Buffer Size (保護緩衝區大小)。依據 RFC 4217 規範，在 TLS/SSL 加密通道下必須先執行 PBSZ 0
        ftpsClient.execPBSZ(0);

        // - execPROT("P")：Data Channel Protection Level (資料通道保護等級)。
        //   "P" (Private) 代表資料通道以 TLS 全程加密傳送，確保檔案本體資料傳輸具備最高機密性與完整性；
        //   若未設定或設為 "C" (Clear)，部分 FTPS 伺服器會拒絕傳輸，或導致檔案內容以明文裸奔。
        ftpsClient.execPROT("P");

        // 步驟 8：切換傳輸通道模式 (被動模式 Passive vs 主動模式 Active)
        // - 被動模式 (Passive Mode, PASV/EPSV)：由伺服器開啟資料埠讓客戶端連入，為穿透防火牆、NAT 及容器化 (Docker/K8s) 環境之標準做法。
        // - 主動模式 (Active Mode, PORT/EPRT)：由客戶端開啟資料埠供伺服器連回，若客戶端在 NAT/防火牆後方極易連線逾時失敗。
        if (config.isPassiveMode()) {
            ftpsClient.enterLocalPassiveMode();
        } else {
            ftpsClient.enterLocalActiveMode();
        }

        // 步驟 9：強制鎖定傳輸型態為二進位模式 (Binary Mode)
        // 預設的 ASCII 模式會自動替換文字換行符號 (\r\n)，導致二進位檔案 (如圖片、ZIP、PDF、執行檔) 內容損壞
        ftpsClient.setFileType(FTP.BINARY_FILE_TYPE);

        log.info("[FTPS] 連線、TLS 交握與身分驗證成功: {}:{} (PROT=P, passiveMode={})",
                config.getHost(), config.getPort(), config.isPassiveMode());

        return ftpsClient;
    }
}
