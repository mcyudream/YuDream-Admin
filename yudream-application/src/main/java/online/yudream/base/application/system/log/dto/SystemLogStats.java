package online.yudream.base.application.system.log.dto;

/**
 * 系统日志缓冲状态：当前缓存条数、因上限被自动清理的条数与缓存上限。
 */
public record SystemLogStats(long size, long droppedCount, int maxEntries) {
}
