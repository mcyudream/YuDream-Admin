package online.yudream.base.domain.platform.milky.sandbox;

import java.util.Locale;

public enum QqSandboxRandomMode {
    REAL,
    FORCE_HIT,
    FORCE_MISS;

    public static QqSandboxRandomMode from(String value) {
        if (value == null || value.isBlank()) return REAL;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("QQ 沙箱随机模式无效：" + value);
        }
    }
}
