package online.yudream.base.domain.system.log.model;

import java.util.Locale;

/**
 * 系统运行日志级别，与 SLF4J/JUL 级别一一对应，供前端级别筛选使用。
 */
public enum SystemLogLevel {

    TRACE, DEBUG, INFO, WARN, ERROR;

    public static SystemLogLevel fromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        for (SystemLogLevel level : values()) {
            if (level.name().equals(normalized)) {
                return level;
            }
        }
        return null;
    }
}
