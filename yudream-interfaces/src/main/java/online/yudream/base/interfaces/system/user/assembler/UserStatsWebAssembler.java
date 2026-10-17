package online.yudream.base.interfaces.system.user.assembler;

import online.yudream.base.application.system.user.dto.UserStatsDTO;
import online.yudream.base.interfaces.system.user.res.UserStatsRes;

import java.util.List;

public class UserStatsWebAssembler {

    private UserStatsWebAssembler() {
    }

    public static UserStatsRes toRes(UserStatsDTO dto) {
        return new UserStatsRes(dto.total(), dto.verifiedCount(), dto.activeCount(), dto.disabledCount(),
                dto.newToday(), dto.newThisWeek(), dto.trendDays(), toTrendResList(dto.registeredTrend()),
                toNamedCountResList(dto.roleDistribution()), toNamedCountResList(dto.completeness()));
    }

    public static List<UserStatsRes.TrendRes> toTrendResList(List<UserStatsDTO.TrendDTO> items) {
        return items == null ? List.of() : items.stream()
                .map(item -> new UserStatsRes.TrendRes(item.date(), item.count()))
                .toList();
    }

    public static List<UserStatsRes.NamedCountRes> toNamedCountResList(List<UserStatsDTO.NamedCountDTO> items) {
        return items == null ? List.of() : items.stream()
                .map(item -> new UserStatsRes.NamedCountRes(item.name(), item.count()))
                .toList();
    }
}
