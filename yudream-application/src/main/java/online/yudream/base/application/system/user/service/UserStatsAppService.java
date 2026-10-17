package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.dto.UserStatsDTO;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserStatsRepo;
import online.yudream.base.domain.system.user.valobj.UserStatistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 用户统计概览：仓储负责全量聚合，本服务只做日期补齐、角色名解析与固定展示顺序。 */
@Service
@RequiredArgsConstructor
public class UserStatsAppService {

    /** 趋势固定展示近 30 天（含今天）。 */
    private static final int TREND_DAYS = 30;

    /** 月度趋势固定展示近 12 个月（含本月）。 */
    private static final int TREND_MONTHS = 12;

    /** 部门分布最多逐项展示的数量，其余合并为「其他 N 个部门」。 */
    private static final int DEPT_TOP_N = 8;

    /** 未分配部门的展示名。 */
    private static final String DEPT_UNASSIGNED_NAME = "未分配部门";

    /** 部门已被删除或 deptId 无对应部门时的占位名，计数不丢弃。 */
    private static final String UNKNOWN_DEPT_NAME = "未知部门";

    /** 角色已被删除或 roleId 无对应角色时的占位名，计数不丢弃。 */
    private static final String UNKNOWN_ROLE_NAME = "未知角色";

    private final UserStatsRepo userStatsRepo;
    private final RoleRepo roleRepo;
    private final DeptRepo deptRepo;

    @Transactional(readOnly = true)
    public UserStatsDTO statistics() {
        LocalDate today = LocalDate.now();
        LocalDate trendFrom = today.minusDays(TREND_DAYS - 1L);
        String monthlyFrom = YearMonth.from(today).minusMonths(TREND_MONTHS - 1L).toString();
        UserStatistics stats = userStatsRepo.statistics(trendFrom, monthlyFrom);
        return new UserStatsDTO(stats.total(), stats.verifiedCount(), stats.activeCount(), stats.disabledCount(),
                stats.newToday(), stats.newThisWeek(), TREND_DAYS, TREND_MONTHS,
                trend(trendFrom, stats.dailyNews()), monthlyTrend(today, stats.monthlyNews()),
                roleDistribution(stats.roleCounts()), deptDistribution(stats), completeness(stats));
    }

    /** 近 12 个月逐月补齐：仓储只回有点数的月份，缺失月份按 0 展示。 */
    private List<UserStatsDTO.TrendDTO> monthlyTrend(LocalDate today, List<UserStatistics.MonthlyNew> monthlyNews) {
        Map<String, Long> counts = monthlyNews.stream().collect(Collectors.toMap(
                UserStatistics.MonthlyNew::month, UserStatistics.MonthlyNew::count, Long::sum));
        YearMonth start = YearMonth.from(today).minusMonths(TREND_MONTHS - 1L);
        List<UserStatsDTO.TrendDTO> trend = new ArrayList<>(TREND_MONTHS);
        for (int offset = 0; offset < TREND_MONTHS; offset++) {
            String month = start.plusMonths(offset).toString();
            trend.add(new UserStatsDTO.TrendDTO(month, counts.getOrDefault(month, 0L)));
        }
        return trend;
    }

    /** 部门分布：解析部门名、补「未分配部门」并按人数降序，只逐项展示前 N 个，其余合并为「其他」。 */
    private List<UserStatsDTO.NamedCountDTO> deptDistribution(UserStatistics stats) {
        Map<Long, Long> deptCounts = stats.deptCounts() == null ? Map.of() : stats.deptCounts();
        Map<Long, String> deptNames = deptCounts.isEmpty() ? Map.of()
                : deptRepo.findByIds(List.copyOf(deptCounts.keySet())).stream()
                        .collect(Collectors.toMap(Dept::getId, Dept::getName, (left, right) -> left));
        List<UserStatsDTO.NamedCountDTO> buckets = new ArrayList<>();
        long assignedTotal = 0;
        for (Map.Entry<Long, Long> entry : deptCounts.entrySet()) {
            assignedTotal += entry.getValue();
            buckets.add(new UserStatsDTO.NamedCountDTO(
                    deptNames.getOrDefault(entry.getKey(), UNKNOWN_DEPT_NAME), entry.getValue()));
        }
        long unassigned = Math.max(stats.total() - assignedTotal, 0);
        if (unassigned > 0) {
            buckets.add(new UserStatsDTO.NamedCountDTO(DEPT_UNASSIGNED_NAME, unassigned));
        }
        List<UserStatsDTO.NamedCountDTO> sorted = buckets.stream()
                .sorted(Comparator.comparingLong(UserStatsDTO.NamedCountDTO::count).reversed())
                .toList();
        if (sorted.size() <= DEPT_TOP_N) {
            return sorted;
        }
        long rest = sorted.subList(DEPT_TOP_N, sorted.size()).stream()
                .mapToLong(UserStatsDTO.NamedCountDTO::count).sum();
        List<UserStatsDTO.NamedCountDTO> merged = new ArrayList<>(sorted.subList(0, DEPT_TOP_N));
        merged.add(new UserStatsDTO.NamedCountDTO("其他 " + (sorted.size() - DEPT_TOP_N) + " 个部门", rest));
        return merged;
    }

    /** 近 30 天逐日补齐：仓储只回有点数的日期，缺失日期按 0 展示。 */
    private List<UserStatsDTO.TrendDTO> trend(LocalDate trendFrom, List<UserStatistics.DailyNew> dailyNews) {
        Map<LocalDate, Long> counts = dailyNews.stream().collect(Collectors.toMap(
                UserStatistics.DailyNew::date, UserStatistics.DailyNew::count, Long::sum));
        List<UserStatsDTO.TrendDTO> trend = new ArrayList<>(TREND_DAYS);
        for (int offset = 0; offset < TREND_DAYS; offset++) {
            LocalDate date = trendFrom.plusDays(offset);
            trend.add(new UserStatsDTO.TrendDTO(date.toString(), counts.getOrDefault(date, 0L)));
        }
        return trend;
    }

    /** 角色分布：按 count 降序（同 count 按 roleId 升序）解析角色名；未匹配到的 roleId 保留计数并占位为「未知角色」。 */
    private List<UserStatsDTO.NamedCountDTO> roleDistribution(Map<Long, Long> roleCounts) {
        if (roleCounts == null || roleCounts.isEmpty()) {
            return List.of();
        }
        Map<Long, String> roleNames = roleRepo.findByIds(List.copyOf(roleCounts.keySet())).stream()
                .collect(Collectors.toMap(Role::getId, Role::getName, (left, right) -> left));
        return roleCounts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new UserStatsDTO.NamedCountDTO(
                        roleNames.getOrDefault(entry.getKey(), UNKNOWN_ROLE_NAME), entry.getValue()))
                .toList();
    }

    /** 资料完整度：固定 5 项与顺序。 */
    private List<UserStatsDTO.NamedCountDTO> completeness(UserStatistics stats) {
        return List.of(
                new UserStatsDTO.NamedCountDTO("已填部门", stats.deptFilledCount()),
                new UserStatsDTO.NamedCountDTO("已分配角色", stats.roleFilledCount()),
                new UserStatsDTO.NamedCountDTO("已填邮箱", stats.emailFilledCount()),
                new UserStatsDTO.NamedCountDTO("已填手机", stats.phoneFilledCount()),
                new UserStatsDTO.NamedCountDTO("已绑定 QQ", stats.qqFilledCount()));
    }
}
