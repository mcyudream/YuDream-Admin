package online.yudream.base.application.system.user.dto;

import java.util.List;

/** 后台用户统计概览：一次聚合给出总览计数、近 N 天注册趋势、角色分布与资料完整度。 */
public record UserStatsDTO(long total, long verifiedCount, long activeCount, long disabledCount,
                           long newToday, long newThisWeek, int trendDays,
                           List<TrendDTO> registeredTrend, List<NamedCountDTO> roleDistribution,
                           List<NamedCountDTO> completeness) {

    /** 单日注册趋势点，date 为 yyyy-MM-dd 字符串。 */
    public record TrendDTO(String date, long count) {
    }

    /** 名称 + 数量结构，用于角色分布与资料完整度。 */
    public record NamedCountDTO(String name, long count) {
    }
}
