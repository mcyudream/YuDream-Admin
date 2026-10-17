package online.yudream.base.application.system.user.dto;

import java.util.List;

/**
 * 后台用户统计概览：一次聚合给出总览计数、日/月注册趋势、角色与部门分布、资料完整度。
 * 所有计数来自仓储全量聚合，不依赖分页结果。
 */
public record UserStatsDTO(long total, long verifiedCount, long activeCount, long disabledCount,
                           long newToday, long newThisWeek, int trendDays, int trendMonths,
                           List<TrendDTO> registeredTrend, List<TrendDTO> monthlyTrend,
                           List<NamedCountDTO> roleDistribution, List<NamedCountDTO> deptDistribution,
                           List<NamedCountDTO> completeness) {

    /** 单个趋势点：日趋势 date 为 yyyy-MM-dd，月趋势 date 为 yyyy-MM。 */
    public record TrendDTO(String date, long count) {
    }

    /** 名称 + 数量结构，用于角色分布、部门分布与资料完整度。 */
    public record NamedCountDTO(String name, long count) {
    }
}
