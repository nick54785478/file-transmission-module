package com.example.demo.application.port.in;

import com.example.demo.application.shared.query.ListRemoteFilesQuery;
import com.example.demo.application.shared.view.RemoteFileGottenView;
import java.util.List;

/**
 * 查詢遠端目錄檔案使用案例介面 (ListRemoteFilesUseCase - Inbound Port)
 * <p>
 * 命名規範: in : ... + UseCase (3-8)
 * 定義查詢遠端主機指定目錄下檔案與目錄清單之業務合約。
 * </p>
 */
public interface ListRemoteFilesUseCase {

    /**
     * 執行遠端檔案清單查詢
     *
     * @param query 查詢條件物件
     * @return 遠端檔案檢視清單
     */
    List<RemoteFileGottenView> listRemoteFiles(ListRemoteFilesQuery query);
}
