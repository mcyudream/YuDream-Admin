package online.yudream.base.application.system.log.dto;

import java.util.Map;

/**
 * 系统日志缓冲状态：当前缓存条数、因上限被自动清理的条数、缓存上限与各级别分布。
 * levelCounts 为缓存内真实计数（键为级别名），供页面级别筛选显示分布，不随当前筛选条件变化。
 */
public record SystemLogStats(long size, long droppedCount, int maxEntries, Map<String, Long> levelCounts) {
}
