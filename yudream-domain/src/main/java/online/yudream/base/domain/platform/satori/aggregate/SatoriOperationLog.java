package online.yudream.base.domain.platform.satori.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;

import java.time.LocalDateTime;

/** Sanitized operational trace for one Satori connection. */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SatoriOperationLog extends BaseDomain {
    private Long connectionId;
    private String level;
    private String category;
    private String action;
    private String detail;
    private LocalDateTime occurredAt;

    public static SatoriOperationLog create(Long connectionId, String level, String category, String action, String detail) {
        return SatoriOperationLog.builder()
                .connectionId(connectionId)
                .level(value(level, "INFO"))
                .category(value(category, "SYSTEM"))
                .action(value(action, "unknown"))
                .detail(sanitize(detail))
                .occurredAt(LocalDateTime.now())
                .build();
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) return null;
        String compact = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compact.length() <= 512 ? compact : compact.substring(0, 512);
    }
}
