package com.example.demo.application.shared.vo;

import java.io.Serializable;

/**
 * 遠端檔案路徑數值物件 (RemoteFilePath - Value Object Record)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>應用層共用數值物件 (Shared VO Record)，封裝遠端伺服器 (FTP / FTPS / SFTP) 的 UNIX 風格路徑結構。</li>
 *   <li>自動處理反斜線替換、連續斜線合併、父目錄解析與結尾斜線清理，確保不同通訊協定路徑之一致性。</li>
 *   <li>具備不可變性 (Immutable)，提供 resolve、getParent、getFileName 等安全路徑操作方法。</li>
 * </ul>
 * </p>
 *
 * @param value 正規化後的 UNIX 風格遠端路徑字串
 */
public record RemoteFilePath(String value) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 精簡建構子 (Compact Constructor)：自動將路徑以 UNIX 格式規範化
     *
     * @param value 原始路徑字串，不可為空
     */
    public RemoteFilePath {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("遠端路徑不可為空");
        }
        value = normalize(value);
    }

    /**
     * 便捷工廠方法建立 RemoteFilePath
     *
     * @param path 路徑字串
     * @return RemoteFilePath 不可變數值物件
     */
    public static RemoteFilePath of(String path) {
        return new RemoteFilePath(path);
    }

    /**
     * 結合子目錄或檔名產生新的 RemoteFilePath
     *
     * @param subPath 子路徑或檔名
     * @return 結合後的 RemoteFilePath 物件
     */
    public RemoteFilePath resolve(String subPath) {
        if (subPath == null || subPath.trim().isEmpty()) {
            return this;
        }
        String combined = this.value + "/" + subPath.trim();
        return new RemoteFilePath(combined);
    }

    /**
     * 取得該路徑的檔案名稱或目錄名稱
     *
     * @return 結尾檔名
     */
    public String getFileName() {
        int lastSlash = value.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < value.length() - 1) {
            return value.substring(lastSlash + 1);
        }
        return value;
    }

    /**
     * 取得上層目錄路徑物件，若已是根目錄則回傳 "/"
     *
     * @return 上層目錄路徑 RemoteFilePath 物件
     */
    public RemoteFilePath getParent() {
        int lastSlash = value.lastIndexOf('/');
        if (lastSlash > 0) {
            return new RemoteFilePath(value.substring(0, lastSlash));
        } else if (lastSlash == 0) {
            return new RemoteFilePath("/");
        }
        return new RemoteFilePath("/");
    }

    /**
     * 取得正規化後的遠端路徑字串 (相容方法)
     *
     * @return 正規化路徑字串
     */
    public String getValue() {
        return value;
    }

    private static String normalize(String path) {
        String trimmed = path.trim().replace('\\', '/');
        // 取代連續的斜線為單一斜線
        String collapsed = trimmed.replaceAll("/+", "/");
        // 若以斜線結尾且長度大於 1，移除結尾斜線
        if (collapsed.length() > 1 && collapsed.endsWith("/")) {
            collapsed = collapsed.substring(0, collapsed.length() - 1);
        }
        return collapsed;
    }

    @Override
    public String toString() {
        return value;
    }
}
