package com.example.demo.presentation.assembler;

import com.example.demo.application.shared.command.CreateServerCredentialCommand;
import com.example.demo.application.shared.command.UpdateServerCredentialCommand;
import com.example.demo.application.shared.dto.ServerCredentialGottenResult;
import com.example.demo.presentation.resource.in.CreateServerCredentialResource;
import com.example.demo.presentation.resource.in.UpdateServerCredentialResource;
import com.example.demo.presentation.resource.out.ServerCredentialRetrievedResource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 伺服器傳輸憑證資源組裝轉換器 (ServerCredentialResourceAssembler - Presentation Assembler)
 * <p>
 * <b>架構與設計職責說明 (AGENTS.md 第 2 節防腐層規範)：</b>
 * <ul>
 *   <li>位於表現層 <code>presentation/assembler/</code>。</li>
 *   <li>作為表現層 (REST Resources) 與應用層 (Commands / DTOs) 之間的防腐轉換器 (ACL)。</li>
 *   <li>嚴格確保 Resource 本身為純資料載體，所有物件轉換一律由此組裝器統一處理。</li>
 * </ul>
 * </p>
 */
@Component
public class ServerCredentialResourceAssembler {

    /**
     * 將建立憑證請求資源轉換為應用層命令
     */
    public CreateServerCredentialCommand toCreateCommand(CreateServerCredentialResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("CreateServerCredentialResource 不能為空");
        }
        return new CreateServerCredentialCommand(
                resource.alias(),
                resource.host(),
                resource.port(),
                resource.protocol(),
                resource.username(),
                resource.password(),
                resource.privateKey(),
                resource.passphrase(),
                resource.description()
        );
    }

    /**
     * 將更新憑證請求資源轉換為應用層命令
     */
    public UpdateServerCredentialCommand toUpdateCommand(String id, UpdateServerCredentialResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("UpdateServerCredentialResource 不能為空");
        }
        return new UpdateServerCredentialCommand(
                id,
                resource.alias(),
                resource.host(),
                resource.port(),
                resource.protocol(),
                resource.username(),
                resource.password(),
                resource.privateKey(),
                resource.passphrase(),
                resource.description(),
                resource.active()
        );
    }

    /**
     * 將應用層 DTO 轉換為對外回應資源
     */
    public ServerCredentialRetrievedResource toRetrievedResource(ServerCredentialGottenResult result) {
        if (result == null) {
            return null;
        }
        return new ServerCredentialRetrievedResource(
                result.id(),
                result.alias(),
                result.host(),
                result.port(),
                result.protocol(),
                result.username(),
                result.maskedPassword(),
                result.maskedPrivateKey(),
                result.maskedPassphrase(),
                result.hasPassword(),
                result.hasPrivateKey(),
                result.hasPassphrase(),
                result.active(),
                result.description(),
                result.createdAt(),
                result.updatedAt()
        );
    }

    /**
     * 將應用層 DTO 列表批量轉換為回應資源列表
     */
    public List<ServerCredentialRetrievedResource> toRetrievedResourceList(List<ServerCredentialGottenResult> results) {
        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }
        return results.stream().map(this::toRetrievedResource).toList();
    }
}
