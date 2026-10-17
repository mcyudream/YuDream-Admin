package online.yudream.base.domain.system.user.valobj;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 后台用户统计聚合结果：全部指标由仓储对全量用户一次聚合得出，不依赖分页结果。
 *
 * @param total            用户总数
 * @param verifiedCount    邮箱已验证用户数
 * @param activeCount      状态为启用的用户数
 * @param disabledCount    状态为停用的用户数
 * @param newToday         服务器本地时区当天 00:00 起创建的用户数
 * @param newThisWeek      服务器本地时区本周（周一 00:00 起，ISO 周）创建的用户数
 * @param deptFilledCount  已填部门（depts 非空）的用户数
 * @param roleFilledCount  已分配角色（roleIds 非空）的用户数
 * @param emailFilledCount 已填邮箱的用户数
 * @param phoneFilledCount 已填手机的用户数
 * @param qqFilledCount    已绑定 QQ 的用户数
 * @param dailyNews        趋势区间内的按日新增，仅包含有点数的日期；date 为服务器本地时区日期，
 *                         消费方需自行补齐缺失日期
 * @param roleCounts       按 roleId 聚合的用户数；同一用户重复的 roleId 只计一次，
 *                         实现按 count 降序、同 count 按 roleId 升序返回（顺序仅便于消费，不做契约）
 */
public record UserStatistics(long total, long verifiedCount, long activeCount, long disabledCount,
                             long newToday, long newThisWeek, long deptFilledCount, long roleFilledCount,
                             long emailFilledCount, long phoneFilledCount, long qqFilledCount,
                             List<DailyNew> dailyNews, Map<Long, Long> roleCounts) {

    /** 单日新增用户数，date 为服务器本地时区日期。 */
    public record DailyNew(LocalDate date, long count) {
    }
}
