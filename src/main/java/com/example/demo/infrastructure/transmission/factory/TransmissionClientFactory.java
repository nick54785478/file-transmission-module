package com.example.demo.infrastructure.transmission.factory;

import com.example.demo.application.shared.exception.FileTransmissionException;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.example.demo.infrastructure.transmission.client.TransmissionProtocolClient;
import org.springframework.stereotype.Component;

/**
 * 傳輸協定客戶端工廠類別 (TransmissionClientFactory - Factory)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>位於基礎設施傳輸層 (<code>infrastructure/transmission/client/</code>)。</li>
 *   <li>負責管理、註冊與分派所有實作 {@link TransmissionProtocolClient} 的具體技術客戶端 (FTP, FTPS, SFTP)。</li>
 *   <li>依賴 Spring 自動注入的所有通訊協定客戶端實例，並以內部 Map 建立各協定索引，提供高效率查詢與解析。</li>
 *   <li>將客戶端查找與管理邏輯自 {@code FileTransmissionGatewayAdapter} 剝離，實現單一職責原則 (SRP)。</li>
 * </ul>
 * </p>
 */
@Component
public class TransmissionClientFactory {

    private final Map<TransmissionProtocol, TransmissionProtocolClient> clientMap;

    /**
     * 建構傳輸協定客戶端工廠並自動依支援協定建立索引
     *
     * @param clients Spring 容器中所有傳輸協定客戶端實例清單
     */
    public TransmissionClientFactory(List<TransmissionProtocolClient> clients) {
        Objects.requireNonNull(clients, "clients 清單不能為空");
        Map<TransmissionProtocol, TransmissionProtocolClient> map = new EnumMap<>(TransmissionProtocol.class);
        for (TransmissionProtocol protocol : TransmissionProtocol.values()) {
            for (TransmissionProtocolClient client : clients) {
                if (client.supports(protocol)) {
                    map.put(protocol, client);
                    break;
                }
            }
        }
        this.clientMap = Collections.unmodifiableMap(map);
    }

    /**
     * 依據傳輸協定獲取相應的技術客戶端實例
     *
     * @param protocol 傳輸協定列舉 (FTP, FTPS, SFTP)
     * @return 支援該協定的 {@link TransmissionProtocolClient} 客戶端實例
     * @throws FileTransmissionException 當未配置或不支援該協定客戶端時拋出
     */
    public TransmissionProtocolClient getClient(TransmissionProtocol protocol) {
        if (protocol == null) {
            throw new IllegalArgumentException("傳輸協定 protocol 不能為空");
        }
        TransmissionProtocolClient client = clientMap.get(protocol);
        if (client == null) {
            throw new FileTransmissionException("未找到支援該協定的傳輸客戶端: " + protocol);
        }
        return client;
    }

    /**
     * 檢查指定傳輸協定是否具備可用之客戶端
     *
     * @param protocol 傳輸協定列舉
     * @return 若具備可用客戶端則回傳 true，否則回傳 false
     */
    public boolean supports(TransmissionProtocol protocol) {
        return protocol != null && clientMap.containsKey(protocol);
    }

    /**
     * 獲取目前工廠已註冊的所有支援協定清單
     *
     * @return 支援協定清單
     */
    public List<TransmissionProtocol> getSupportedProtocols() {
        return List.copyOf(clientMap.keySet());
    }
}
