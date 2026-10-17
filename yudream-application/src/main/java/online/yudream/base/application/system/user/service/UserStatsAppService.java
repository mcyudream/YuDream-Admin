package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.dto.UserStatsDTO;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserStatsRepo;
import online.yudream.base.domain.system.user.valobj.UserStatistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    /** 角色已被删除或 roleId 无对应角色时的占位名，计数不丢弃。 */
    private static final String UNKNOWN_ROLE_NAME = "未知角色";

    private final UserStatsRepo userStatsRepo;
    private final RoleRepo roleRepo;

    @Transactional(readOnly = true)
    public UserStatsDTO statistics() {
        LocalDate today = LocalDate.now();
        LocalDate trendFrom = today.minusDays(TREND_DAYS - 1L);
        UserStatistics stats = userStatsRepo.statistics(trendFrom);
        return new UserStatsDTO(stats.total(), stats.verifiedCount(), stats.activeCount(), stats.disabledCount(),
                stats.newToday(), stats.newThisWeek(), TREND_DAYS, trend(trendFrom, stats.dailyNews()),
                roleDistribution(stats.roleCounts()), completeness(stats));
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
