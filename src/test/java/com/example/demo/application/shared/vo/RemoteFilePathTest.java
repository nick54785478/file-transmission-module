package com.example.demo.application.shared.vo;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 遠端檔案路徑數值物件單元測試 (RemoteFilePathTest)
 * <p>
 * 遵循測試規範 (AGENTS.md 第 5 節)：
 * 1. 涵蓋路徑正規化、父目錄解析、主檔名與副檔名切分、方案 A 歷史備份路徑產生邏輯；
 * 2. 測試方法均撰寫 Gherkin 語法 Javadoc (Feature, Scenario, Given-When-Then)。
 * </p>
 */
class RemoteFilePathTest {

    /**
     * Feature: 遠端路徑規範化與不可變封裝
     * Scenario: 當輸入反斜線、重複斜線或結尾斜線時，自動規範化為標準 UNIX 風格路徑
     * Given: 包含反斜線與重複斜線之路徑字串
     * When: 建立 RemoteFilePath 實例
     * Then: 路徑被轉換為乾淨之 UNIX 斜線表示法
     */
    @Test
    @DisplayName("情境：自動規範化 Windows 反斜線與連續斜線")
    void testNormalization() {
        RemoteFilePath path = RemoteFilePath.of("\\remote\\\\dir//sub\\file.txt");
        assertEquals("/remote/dir/sub/file.txt", path.getValue());

        RemoteFilePath dirPath = RemoteFilePath.of("/upload/files/");
        assertEquals("/upload/files", dirPath.getValue());
    }

    /**
     * Feature: 空路徑防禦性驗證
     * Scenario: 傳入 null 或空字串應拋出 IllegalArgumentException
     * Given: null 或空字串
     * When: 建立 RemoteFilePath
     * Then: 拋出 IllegalArgumentException
     */
    @Test
    @DisplayName("情境：空路徑防禦性驗證")
    void testBlankPathThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> RemoteFilePath.of(null));
        assertThrows(IllegalArgumentException.class, () -> RemoteFilePath.of("   "));
    }

    /**
     * Feature: 檔名、主檔名與副檔名解析
     * Scenario: 正確解析包含多個小數點或無副檔名之檔案路徑
     * Given: 包含副檔名與多個點的遠端路徑
     * When: 呼叫 getFileName, getBaseName, getExtension
     * Then: 正確擷取各部分字串
     */
    @Test
    @DisplayName("情境：正確解析檔名、主檔名與副檔名")
    void testFileNameBaseNameAndExtension() {
        RemoteFilePath path = RemoteFilePath.of("/data/reports/monthly_report.2026.csv");
        assertEquals("monthly_report.2026.csv", path.getFileName());
        assertEquals("monthly_report.2026", path.getBaseName());
        assertEquals(".csv", path.getExtension());

        RemoteFilePath noExtPath = RemoteFilePath.of("/data/bin/executable");
        assertEquals("executable", noExtPath.getFileName());
        assertEquals("executable", noExtPath.getBaseName());
        assertEquals("", noExtPath.getExtension());
    }

    /**
     * Feature: 父目錄解析與子路徑結合
     * Scenario: 解析父目錄與使用 resolve 結合子路徑
     * Given: 遠端路徑
     * When: 呼叫 getParent 與 resolve
     * Then: 產生正確之上層路徑與結合後的子路徑
     */
    @Test
    @DisplayName("情境：父目錄解析與子路徑結合")
    void testParentAndResolve() {
        RemoteFilePath path = RemoteFilePath.of("/remote/data/file.txt");
        assertEquals("/remote/data", path.getParent().getValue());

        RemoteFilePath rootChild = RemoteFilePath.of("/file.txt");
        assertEquals("/", rootChild.getParent().getValue());

        RemoteFilePath combined = path.getParent().resolve("archive.bak");
        assertEquals("/remote/data/archive.bak", combined.getValue());
    }

    /**
     * Feature: 方案 A 歷史備份歸檔路徑產生
     * Scenario: 根據給定時間點產生包含 timestamp 與 .bak 之歷史歸檔路徑
     * Given: 指定時間點 2026-10-18 17:30:00 與遠端檔案路徑 /upload/report.txt
     * When: 呼叫 toBackupPath(timestamp)
     * Then: 產生 /upload/report_20261018_173000.bak
     */
    @Test
    @DisplayName("情境：方案 A 產生歷史歸檔備份路徑 (.bak)")
    void testToBackupPath() {
        RemoteFilePath path = RemoteFilePath.of("/upload/report.txt");
        LocalDateTime testTime = LocalDateTime.of(2026, 10, 18, 17, 30, 0);

        RemoteFilePath backupPath = path.toBackupPath(testTime);

        assertEquals("/upload/report_20261018_173000.bak", backupPath.getValue());
        assertEquals("report_20261018_173000.bak", backupPath.getFileName());
        assertEquals("/upload", backupPath.getParent().getValue());
    }
}
