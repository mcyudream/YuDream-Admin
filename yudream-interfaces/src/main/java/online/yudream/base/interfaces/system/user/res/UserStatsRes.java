package online.yudream.base.interfaces.system.user.res;

import java.util.List;

/** 后台用户统计概览响应；数值字段沿用全局 Long → 字符串序列化约定。 */
public record UserStatsRes(long total, long verifiedCount, long activeCount, long disabledCount,
                           long newToday, long newThisWeek, int trendDays, int trendMonths,
                           List<TrendRes> registeredTrend, List<TrendRes> monthlyTrend,
                           List<NamedCountRes> roleDistribution, List<NamedCountRes> deptDistribution,
                           List<NamedCountRes> completeness) {

    /** 单个趋势点：日趋势 date 为 yyyy-MM-dd，月趋势 date 为 yyyy-MM。 */
    public record TrendRes(String date, long count) {
    }

    /** 名称 + 数量结构，用于角色分布与资料完整度。 */
    public record NamedCountRes(String name, long count) {
    }
}
