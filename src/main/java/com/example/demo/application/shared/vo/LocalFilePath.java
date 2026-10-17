package com.example.demo.application.shared.vo;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本機檔案路徑數值物件 (LocalFilePath - Value Object Record)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>應用層共用數值物件 (Shared VO Record)，封裝本機作業系統檔案或目錄路徑。</li>
 *   <li>自動執行絕對路徑轉換與路徑正規化 (Path Normalization)，防止目錄遍歷攻擊並確保跨平台一致性。</li>
 *   <li>提供不可變性 (Immutable)，封裝存在性檢查、檔案/目錄類型判斷與路徑結合 (resolve) 行為。</li>
 * </ul>
 * </p>
 *
 * @param value 正規化後的絕對路徑字串
 */
public record LocalFilePath(String value) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 精簡建構子 (Compact Constructor)：自動將路徑轉為絕對路徑並正規化
     *
     * @param value 原始路徑字串，不可為空
     */
    public LocalFilePath {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("本機路徑不可為空");
        }
        value = Paths.get(value.trim()).toAbsolutePath().normalize().toString();
    }

    /**
     * 便捷工廠方法建立 LocalFilePath
     *
     * @param path 路徑字串
     * @return LocalFilePath 不可變數值物件
     */
    public static LocalFilePath of(String path) {
        return new LocalFilePath(path);
    }

    /**
     * 取得底層 java.io.File 物件
     *
     * @return File 物件
     */
    public File toFile() {
        return new File(value);
    }

    /**
     * 取得底層 java.nio.file.Path 物件
     *
     * @return Path 物件
     */
    public Path toPath() {
        return Paths.get(value);
    }

    /**
     * 檢查檔案或目錄是否存在
     *
     * @return 若存在回傳 true，否則回傳 false
     */
    public boolean exists() {
        return toFile().exists();
    }

    /**
     * 檢查是否為目錄
     *
     * @return 若為目錄回傳 true，否則回傳 false
     */
    public boolean isDirectory() {
        return toFile().isDirectory();
    }

    /**
     * 檢查是否為一般檔案
     *
     * @return 若為一般檔案回傳 true，否則回傳 false
     */
    public boolean isFile() {
        return toFile().isFile();
    }

    /**
     * 取得檔案名稱或最後目錄名稱
     *
     * @return 檔名字串
     */
    public String getFileName() {
        return toFile().getName();
    }

    /**
     * 取得檔案最後修改時間戳記 (毫秒)
     *
     * @return 最後修改時間毫秒數
     */
    public long getLastModified() {
        return toFile().lastModified();
    }

    /**
     * 結合子目錄或檔名產生新的 LocalFilePath 物件
     *
     * @param childName 子目錄或檔名
     * @return 結合後的 LocalFilePath 物件
     */
    public LocalFilePath resolve(String childName) {
        Path resolved = Paths.get(value).resolve(childName).normalize();
        return new LocalFilePath(resolved.toString());
    }

    /**
     * 取得正規化後的絕對路徑字串 (相容方法)
     *
     * @return 路徑字串
     */
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
