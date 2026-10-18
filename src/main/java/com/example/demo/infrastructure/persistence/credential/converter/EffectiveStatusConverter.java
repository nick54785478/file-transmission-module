package com.example.demo.infrastructure.persistence.credential.converter;

import com.example.demo.application.shared.vo.EffectiveStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 是否生效狀態 JPA 屬性轉換器 (EffectiveStatusConverter - Infrastructure Persistence Converter)
 * <p>
 * <b>架構與設計職責說明 (AGENTS.md 2)：</b>
 * <ul>
 *   <li>位於基礎設施持久化層 <code>infrastructure/persistence/credential/converter/</code>。</li>
 *   <li>負責在應用層領域數值物件 {@link EffectiveStatus} 與資料庫欄位 (VARCHAR(1) / CHAR(1)) 之間自動轉換。</li>
 *   <li><b>資料庫落盤規範：</b> 生效儲存為 <code>"Y"</code>，未生效/停用儲存為 <code>"N"</code>。</li>
 * </ul>
 * </p>
 */
@Converter(autoApply = true)
public class EffectiveStatusConverter implements AttributeConverter<EffectiveStatus, String> {

    @Override
    public String convertToDatabaseColumn(EffectiveStatus attribute) {
        if (attribute == null) {
            return EffectiveStatus.INEFFECTIVE.getCode();
        }
        return attribute.getCode();
    }

    @Override
    public EffectiveStatus convertToEntityAttribute(String dbData) {
        return EffectiveStatus.fromCode(dbData);
    }
}
