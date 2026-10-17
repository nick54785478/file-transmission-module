package com.example.demo.application.shared.view;

import java.io.Serializable;

/**
 * 遠端檔案查詢投影檢視 (RemoteFileGottenView - Projection / View)
 * <p>
 * 命名規範: N + Gotten + View (3-7)
 * <br>
 * <b>重要架構守則：</b>
 * 本類別嚴格作為純資料載體 (Pure Data Carrier)，禁止包含任何轉換邏輯；
 * 轉換由 application/transmission/assembler/TransmissionDtoAssembler 統一負責。
 * </p>
 */
public class RemoteFileGottenView implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String fileName;
    private final String fullPath;
    private final long size;
    private final long lastModifiedMillis;
    private final boolean directory;

    public RemoteFileGottenView(
            String fileName,
            String fullPath,
            long size,
            long lastModifiedMillis,
            boolean directory
    ) {
        this.fileName = fileName;
        this.fullPath = fullPath;
        this.size = size;
        this.lastModifiedMillis = lastModifiedMillis;
        this.directory = directory;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFullPath() {
        return fullPath;
    }

    public long getSize() {
        return size;
    }

    public long getLastModifiedMillis() {
        return lastModifiedMillis;
    }

    public boolean isDirectory() {
        return directory;
    }
}
