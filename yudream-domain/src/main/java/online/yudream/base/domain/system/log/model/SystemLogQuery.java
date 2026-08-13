package online.yudream.base.domain.system.log.model;

import java.util.Locale;
import java.util.Set;

/**
 * 系统日志查询过滤条件：级别、模块集合、关键字与最大条数。
 */
public record SystemLogQuery(String level, Set<String> modules, String keyword, int limit) {

    public SystemLogQuery {
        modules = modules == null || modules.isEmpty() ? Set.of() : Set.copyOf(modules);
        level = level == null || level.isBlank() ? null : level.trim().toUpperCase(Locale.ROOT);
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase(Locale.ROOT);
        limit = Math.max(limit, 1);
    }

    public static SystemLogQuery of(String level, Set<String> modules, String keyword, int limit) {
        return new SystemLogQuery(level, modules, keyword, limit);
    }

    public boolean matches(SystemLogEntry entry) {
        if (level != null && !entry.level().name().equals(level)) {
            return false;
        }
        if (!modules.isEmpty() && !modules.contains(entry.module())) {
            return false;
        }
        if (keyword != null) {
            String haystack = (entry.message() == null ? "" : entry.message())
                    + " " + (entry.logger() == null ? "" : entry.logger())
                    + " " + (entry.throwable() == null ? "" : entry.throwable());
            if (!haystack.toLowerCase(Locale.ROOT).contains(keyword)) {
                return false;
            }
        }
        return true;
    }
}
