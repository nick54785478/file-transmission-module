package com.example.demo.infrastructure.persistence.transmission.vo;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 檔案傳輸實體唯一識別碼 (FileTransmissionId - Entity ID)
 * <p>
 * <b>架構與業務職責說明：</b>
 * <ul>
 *   <li>位於 <code>infrastructure/persistence/transmission/entity/</code>。</li>
 *   <li>作為傳輸實體 (TransmissionSession Entity) 的唯一識別碼標識 (Identity)。</li>
 *   <li>具備不可變性 (Immutable)，封裝 UUID 字串生成與驗證邏輯。</li>
 * </ul>
 * </p>
 */
public final class FileTransmissionId implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String value;

    /**
     * 以既有字串識別碼建構
     *
     * @param value 識別碼字串，不可為空
     */
    public FileTransmissionId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("FileTransmissionId 不能為空");
        }
        this.value = value.trim();
    }

    /**
     * 隨機產生全新的 UUID 識別碼實例
     *
     * @return 新的 FileTransmissionId 物件
     */
    public static FileTransmissionId generate() {
        return new FileTransmissionId(UUID.randomUUID().toString());
    }

    /**
     * 取得識別碼字串值
     *
     * @return 識別碼字串
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileTransmissionId that = (FileTransmissionId) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
