package com.example.demo.application.service;

import com.example.demo.application.port.in.ReconcileTransmissionUseCase;
import com.example.demo.application.port.out.FileTransmissionGatewayPort;
import com.example.demo.application.port.out.FileTransmissionTrackerPort;
import com.example.demo.application.port.out.ServerCredentialStoragePort;
import com.example.demo.application.shared.dto.TransmissionReconciliationGottenResult;
import com.example.demo.application.shared.dto.ZombieSessionGottenResult;
import com.example.demo.application.shared.vo.RemoteFilePath;
import com.example.demo.application.shared.vo.TransmissionProtocol;
import com.example.demo.application.shared.vo.TransmissionServerConfig;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * 傳輸作業對帳與自癒應用服務 (TransmissionReconciliationService - Application Service)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>命名規範: <code>in : ... + ApplicationService</code> (AGENTS.md 3-9)。</li>
 *   <li>實作 Inbound Port {@link ReconcileTransmissionUseCase}。</li>
 *   <li>專職負責伺服器重啟、容器崩潰 (OOM / SIGKILL) 後殘留的超時殭屍任務探測與狀態自癒。</li>
 *   <li><b>探測自癒邏輯：</b>
 *     <ol>
 *       <li>檢查遠端正式檔案是否存在且大小相符：若合格則自動推進 DB 狀態為 COMPLETED 終態（附註 [RECONCILED_SUCCESS]）。</li>
 *       <li>若無正式檔但有 .staging 暫存檔：主動刪除遠端殘留暫存檔，並將 DB 標記為 FAILED（附註 [ZOMBIE_TIMEOUT_PURGED]）。</li>
 *       <li>若兩者皆無：直接標記為 FAILED（附註 [ABRUPT_TERMINATION]）。</li>
 *     </ol>
 *   </li>
 *   <li><b>樂觀鎖保護：</b>若在更新時與正在執行的主工作執行緒發生競爭，自動捕捉樂觀鎖異常並略過，確保資料終態一致性。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class TransmissionReconciliationService implements ReconcileTransmissionUseCase {

    private final FileTransmissionTrackerPort trackerPort;
    private final FileTransmissionGatewayPort gatewayPort;
    private final ServerCredentialStoragePort credentialStoragePort;

    /**
     * 建構對帳自癒服務並注入輸出埠依賴
     *
     * @param trackerPort 傳輸生命週期追蹤輸出埠
     * @param gatewayPort 傳輸通訊閘道輸出埠
     * @param credentialStoragePort 伺服器憑證儲存輸出埠
     */
    public TransmissionReconciliationService(
            FileTransmissionTrackerPort trackerPort,
            FileTransmissionGatewayPort gatewayPort,
            ServerCredentialStoragePort credentialStoragePort
    ) {
        this.trackerPort = Objects.requireNonNull(trackerPort, "trackerPort 不能為空");
        this.gatewayPort = Objects.requireNonNull(gatewayPort, "gatewayPort 不能為空");
        this.credentialStoragePort = Objects.requireNonNull(credentialStoragePort, "credentialStoragePort 不能為空");
    }

    @Override
    public TransmissionReconciliationGottenResult reconcile(Duration zombieThreshold) {
        Duration effectiveThreshold = (zombieThreshold != null && !zombieThreshold.isNegative())
                ? zombieThreshold
                : Duration.ofMinutes(30);

        List<ZombieSessionGottenResult> zombies = trackerPort.findZombieSessions(effectiveThreshold);
        if (zombies.isEmpty()) {
            return TransmissionReconciliationGottenResult.empty();
        }

        log.info("[對帳自癒引擎] 發現 {} 筆超時停滯之殭屍傳輸任務 (門檻: {} 分鐘)",
                zombies.size(), effectiveThreshold.toMinutes());

        int selfHealedCount = 0;
        int purgedCount = 0;
        int failedCount = 0;

        for (ZombieSessionGottenResult zombie : zombies) {
            try {
                ReconcileOutcome outcome = reconcileSingleSession(zombie);
                switch (outcome) {
                    case HEALED -> selfHealedCount++;
                    case PURGED -> purgedCount++;
                    case FAILED -> failedCount++;
                    case SKIPPED -> {
                    }
                }
            } catch (OptimisticLockingFailureException olEx) {
                log.warn("[對帳自癒引擎] 任務 {} 發生樂觀鎖版本競爭，表示主執行緒或另一個對帳節點已完成更新，跳過此筆處理",
                        zombie.trackingId());
            } catch (Exception ex) {
                log.error("[對帳自癒引擎] 處理殭屍任務 {} 時發生非預期錯誤: {}", zombie.trackingId(), ex.getMessage(), ex);
                failedCount++;
            }
        }

        log.info("[對帳自癒引擎] 對帳作業完成: 總掃描={}, 自癒成功={}, 暫存清理={}, 逾時失敗={}",
                zombies.size(), selfHealedCount, purgedCount, failedCount);

        return new TransmissionReconciliationGottenResult(
                zombies.size(),
                selfHealedCount,
                purgedCount,
                failedCount
        );
    }

    /**
     * 針對單一殭屍傳輸階段進行探測與自癒
     *
     * @param zombie 殭屍任務資料載體
     * @return 處理結果列舉
     */
    private ReconcileOutcome reconcileSingleSession(ZombieSessionGottenResult zombie) {
        Optional<TransmissionServerConfig> configOpt = resolveServerConfig(zombie);
        if (configOpt.isEmpty()) {
            log.warn("[對帳自癒引擎] 任務 {} 缺乏遠端主機連線資訊或憑證已刪除，直接判定為逾時失敗", zombie.trackingId());
            trackerPort.recordReconciliationFailure(
                    zombie.trackingId(),
                    "[ZOMBIE_NO_CREDENTIALS] 缺乏伺服器連線配置，無法探測遠端狀態"
            );
            return ReconcileOutcome.FAILED;
        }

        TransmissionServerConfig config = configOpt.get();

        // 1. 若目標檔案路徑與檔名齊備，首先探測正式檔案是否已原子提交成功
        if (zombie.targetPath() != null && zombie.fileName() != null) {
            RemoteFilePath targetFilePath = RemoteFilePath.of(zombie.targetPath()).resolve(zombie.fileName());
            if (gatewayPort.existsFile(config, targetFilePath)) {
                long remoteSize = gatewayPort.getRemoteFileSize(config, targetFilePath);
                Long expectedSize = zombie.fileSizeBytes();
                // 若遠端檔案大小存在且 (未指定預期大小或大小完全吻合)
                if (remoteSize >= 0 && (expectedSize == null || expectedSize <= 0 || remoteSize == expectedSize)) {
                    log.info("[對帳自癒引擎] 任務 {} 遠端正式檔案完整存在 ({} bytes)，自癒為 COMPLETED",
                            zombie.trackingId(), remoteSize);
                    trackerPort.recordReconciliationSuccess(zombie.trackingId(), remoteSize);

                    // 若曾有暫存檔遺留，順帶清理
                    cleanStagingFileIfExists(config, zombie.stagingPath());
                    return ReconcileOutcome.HEALED;
                }
            }
        }

        // 2. 若正式檔不存在，探測是否遺留 .staging 暫存檔
        if (zombie.stagingPath() != null) {
            RemoteFilePath stagingPath = RemoteFilePath.of(zombie.stagingPath());
            if (gatewayPort.existsFile(config, stagingPath)) {
                log.info("[對帳自癒引擎] 任務 {} 遠端僅存在暫存檔 {}，發起清理並標記為 FAILED",
                        zombie.trackingId(), stagingPath.getValue());
                gatewayPort.deleteFile(config, stagingPath);
                trackerPort.recordReconciliationFailure(
                        zombie.trackingId(),
                        "[ZOMBIE_TIMEOUT_PURGED] 傳輸中途終止且超時，已清理遠端暫存檔"
                );
                return ReconcileOutcome.PURGED;
            }
        }

        // 3. 兩者皆不存在，標記終態失敗
        log.info("[對帳自癒引擎] 任務 {} 遠端無正式檔亦無暫存檔，標記為 FAILED", zombie.trackingId());
        trackerPort.recordReconciliationFailure(
                zombie.trackingId(),
                "[ABRUPT_TERMINATION] 伺服器異常中斷，遠端無正式檔或暫存檔"
        );
        return ReconcileOutcome.FAILED;
    }

    /**
     * 解析傳輸伺服器連線配置
     *
     * @param zombie 殭屍任務載體
     * @return 伺服器配置 Optional
     */
    private Optional<TransmissionServerConfig> resolveServerConfig(ZombieSessionGottenResult zombie) {
        if (zombie.credentialId() != null && !zombie.credentialId().isBlank()) {
            Optional<TransmissionServerConfig> fromStore = credentialStoragePort.resolveTransmissionConfig(zombie.credentialId());
            if (fromStore.isPresent()) {
                return fromStore;
            }
        }

        if (zombie.serverHost() != null && !zombie.serverHost().isBlank() && zombie.protocol() != null) {
            try {
                TransmissionProtocol protocol = TransmissionProtocol.valueOf(zombie.protocol());
                int port = (zombie.serverPort() != null && zombie.serverPort() > 0)
                        ? zombie.serverPort()
                        : protocol.getDefaultPort();
                String username = (zombie.serverUsername() != null) ? zombie.serverUsername() : "anonymous";
                String password = (zombie.serverPassword() != null) ? zombie.serverPassword() : "";
                return Optional.of(TransmissionServerConfig.of(
                        zombie.serverHost(),
                        port,
                        protocol,
                        username,
                        password
                ));
            } catch (Exception ex) {
                log.warn("[對帳自癒引擎] 解析伺服器配置失敗: {}", ex.getMessage());
            }
        }

        return Optional.empty();
    }

    /**
     * 防禦性清除暫存檔案
     *
     * @param config 伺服器配置
     * @param stagingPathStr 暫存檔案路徑字串
     */
    private void cleanStagingFileIfExists(TransmissionServerConfig config, String stagingPathStr) {
        if (stagingPathStr == null || stagingPathStr.isBlank()) {
            return;
        }
        try {
            RemoteFilePath path = RemoteFilePath.of(stagingPathStr);
            if (gatewayPort.existsFile(config, path)) {
                gatewayPort.deleteFile(config, path);
            }
        } catch (Exception ex) {
            log.warn("[對帳自癒引擎] 清理殘存暫存檔 {} 失敗: {}", stagingPathStr, ex.getMessage());
        }
    }

    /**
     * 單筆對帳執行成果列舉
     */
    private enum ReconcileOutcome {
        HEALED,
        PURGED,
        FAILED,
        SKIPPED
    }
}
