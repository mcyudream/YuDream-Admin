package online.yudream.base.domain.system.user.repo;

import online.yudream.base.domain.system.user.valobj.UserStatistics;

import java.time.LocalDate;

/** 后台用户统计只读投影：一次全量聚合给出计数、趋势、角色分布与资料完整度，禁止从分页结果推导。 */
public interface UserStatsRepo {

    /**
     * 聚合全量用户统计。
     *
     * @param trendFrom 趋势起始日期（含），按服务器本地时区解释
     */
    UserStatistics statistics(LocalDate trendFrom);
}
