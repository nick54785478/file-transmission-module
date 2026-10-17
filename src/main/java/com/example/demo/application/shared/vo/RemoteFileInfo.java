package com.example.demo.application.shared.vo;

import java.io.Serializable;
import java.util.Objects;

/**
 * 遠端檔案中繼資料數值物件 (RemoteFileInfo - Value Object Record)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>應用層共用數值物件 (Shared VO Record)，封裝遠端伺服器 (FTP / FTPS / SFTP) 上檔案或目錄之屬性元資料。</li>
 *   <li>包含檔案名稱、完整遠端路徑 (RemoteFilePath)、位元組大小、最後修改時間戳記及是否為目錄。</li>
 *   <li>作為 FileTransmissionGatewayPort.listFiles 方法的回傳載體，統一各協定底層列表查詢結果。</li>
 * </ul>
 * </p>
 *
 * @param fileName 檔案名稱，不可為空
 * @param fullPath 完整遠端路徑物件，不可為空
 * @param size 檔案大小 (bytes)，若小於 0 則設為 0
 * @param lastModifiedMillis 最後修改時間戳記 (毫秒)
 * @param directory 是否為目錄
 */
public record RemoteFileInfo(
        String fileName,
        RemoteFilePath fullPath,
        long size,
        long lastModifiedMillis,
        boolean directory
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 精簡建構子 (Compact Constructor)：驗證必填欄位並保證 size 大於等於 0
     */
    public RemoteFileInfo {
        Objects.requireNonNull(fileName, "fileName 不能為空");
        Objects.requireNonNull(fullPath, "fullPath 不能為空");
        size = Math.max(0, size);
    }

    /**
     * 取得檔案名稱 (相容方法)
     *
     * @return 檔案名稱
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 取得完整遠端路徑物件 (相容方法)
     *
     * @return RemoteFilePath 物件
     */
    public RemoteFilePath getFullPath() {
        return fullPath;
    }

    /**
     * 取得檔案大小 (位元組) (相容方法)
     *
     * @return 檔案大小 (bytes)
     */
    public long getSize() {
        return size;
    }

    /**
     * 取得最後修改時間戳記 (毫秒) (相容方法)
     *
     * @return 毫秒時間戳記
     */
    public long getLastModifiedMillis() {
        return lastModifiedMillis;
    }

    /**
     * 判斷是否為目錄 (相容方法)
     *
     * @return 若為目錄回傳 true，否則回傳 false
     */
    public boolean isDirectory() {
        return directory;
    }
}
