package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.dto.PluginMarketplaceUpdatePlanDTO;
import online.yudream.base.application.platform.plugin.dto.PluginModuleDTO;
import online.yudream.base.application.platform.plugin.dto.PluginModuleSummaryDTO;
import online.yudream.base.application.platform.plugin.query.PluginModulePageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 插件目录查询：把「运行时插件列表」与「市场更新计划」合成一个可分页的管理视图。
 *
 * 之所以单独成服务：更新状态筛选需要市场侧的计划数据，而 {@link PluginStoreAppService}
 * 已依赖 {@link PluginAppService}，插件侧不能再反向依赖市场侧（会成环）。
 */
@Service
@RequiredArgsConstructor
public class PluginDirectoryAppService {

    private static final String ALL = "ALL";
    private static final String UPDATE_AVAILABLE = "AVAILABLE";
    private static final String UPDATE_BLOCKED = "BLOCKED";
    private static final String SORT_NAME = "NAME";
    private static final String SORT_STATUS = "STATUS";
    private static final String SORT_RECENT = "RECENT";
    private static final int MAX_PAGE_SIZE = 100;

    /** 异常优先、其次运行中：按状态排序时把需要处理的插件排到前面。 */
    private static final Map<PluginStatus, Integer> STATUS_WEIGHT = Map.of(
            PluginStatus.ERROR, 0,
            PluginStatus.ENABLED, 1,
            PluginStatus.LOADED, 2,
            PluginStatus.INSTALLED, 3,
            PluginStatus.DISABLED, 4
    );

    private final PluginAppService pluginAppService;
    private final PluginStoreAppService pluginStoreAppService;

    /** 分页查询：关键词、运行状态、更新状态与排序都在服务端完成，返回当前页记录与筛选后总数。 */
    public PageResult<PluginModuleDTO> page(PluginModulePageQuery query) {
        PluginModulePageQuery condition = query == null ? new PluginModulePageQuery() : query;
        List<PluginModuleDTO> modules = pluginAppService.list();
        String updateState = normalizeUpdateState(condition.getUpdateState());
        Set<String> availableCodes = Set.of();
        Set<String> blockedCodes = Set.of();
        if (!ALL.equals(updateState)) {
            List<PluginMarketplaceUpdatePlanDTO> plans = pluginStoreAppService.updatePlans();
            availableCodes = codesOf(plans, false);
            blockedCodes = codesOf(plans, true);
        }
        String status = normalizeStatus(condition.getStatus());
        String keyword = condition.getKeyword() == null ? "" : condition.getKeyword().trim().toLowerCase(Locale.ROOT);
        Set<String> available = availableCodes;
        Set<String> blocked = blockedCodes;
        List<PluginModuleDTO> filtered = modules.stream()
                .filter(module -> matchesKeyword(module, keyword))
                .filter(module -> ALL.equals(status) || status.equals(statusOf(module)))
                .filter(module -> matchesUpdateState(module, updateState, available, blocked))
                .sorted(comparator(condition.getSort()))
                .toList();

        int size = Math.min(Math.max(condition.getSize(), 1), MAX_PAGE_SIZE);
        int page = Math.max(condition.getPage(), 1);
        int from = Math.min((page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return new PageResult<>(new ArrayList<>(filtered.subList(from, to)), filtered.size(), page, size);
    }

    /** 状态概览：筛选胶囊的计数来自全量分布，分页后的当前页记录无法推导。 */
    public PluginModuleSummaryDTO summary() {
        List<PluginModuleDTO> modules = pluginAppService.list();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (PluginStatus status : PluginStatus.values()) {
            counts.put(status.name(), 0L);
        }
        for (PluginModuleDTO module : modules) {
            counts.merge(statusOf(module), 1L, Long::sum);
        }
        return PluginModuleSummaryDTO.builder()
                .total(modules.size())
                .statusCounts(counts)
                .build();
    }

    private Set<String> codesOf(List<PluginMarketplaceUpdatePlanDTO> plans, boolean blocked) {
        return plans.stream()
                .filter(plan -> plan != null && StringUtils.hasText(plan.getCode()))
                .filter(plan -> blocked == StringUtils.hasText(plan.getBlockedReason()))
                .map(PluginMarketplaceUpdatePlanDTO::getCode)
                .collect(Collectors.toSet());
    }

    private boolean matchesKeyword(PluginModuleDTO module, String keyword) {
        if (keyword.isEmpty()) {
            return true;
        }
        // Stream.of 允许 null：缺版本或描述的插件记录同样要能参与搜索。
        return Stream.of(module.getName(), module.getCode(), module.getVersion(), module.getDescription())
                .filter(StringUtils::hasText)
                .anyMatch(value -> value.toLowerCase(Locale.ROOT).contains(keyword));
    }

    private boolean matchesUpdateState(PluginModuleDTO module, String updateState, Set<String> available, Set<String> blocked) {
        if (ALL.equals(updateState)) {
            return true;
        }
        String code = module.getCode();
        return UPDATE_AVAILABLE.equals(updateState) ? available.contains(code) : blocked.contains(code);
    }

    private Comparator<PluginModuleDTO> comparator(String sort) {
        String normalized = sort == null ? "" : sort.trim().toUpperCase(Locale.ROOT);
        Comparator<PluginModuleDTO> byCode = Comparator.comparing(PluginModuleDTO::getCode, codeOrder());
        return switch (normalized) {
            case SORT_NAME -> Comparator
                    .comparing((PluginModuleDTO module) -> safe(module.getName()), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(byCode);
            case SORT_STATUS -> Comparator
                    .comparingInt((PluginModuleDTO module) -> STATUS_WEIGHT.getOrDefault(module.getStatus(), STATUS_WEIGHT.size()))
                    .thenComparing(byCode);
            case SORT_RECENT -> Comparator
                    .comparing(PluginModuleDTO::getLoadedAt, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                    .thenComparing(byCode);
            default -> byCode;
        };
    }

    private Comparator<String> codeOrder() {
        return Comparator.nullsLast(Comparator.<String>naturalOrder());
    }

    private String statusOf(PluginModuleDTO module) {
        return module.getStatus() == null ? PluginStatus.INSTALLED.name() : module.getStatus().name();
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status) || ALL.equalsIgnoreCase(status.trim())) {
            return ALL;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        for (PluginStatus candidate : PluginStatus.values()) {
            if (candidate.name().equals(normalized)) {
                return normalized;
            }
        }
        return ALL;
    }

    private String normalizeUpdateState(String updateState) {
        if (!StringUtils.hasText(updateState)) {
            return ALL;
        }
        String normalized = updateState.trim().toUpperCase(Locale.ROOT);
        return UPDATE_AVAILABLE.equals(normalized) || UPDATE_BLOCKED.equals(normalized) ? normalized : ALL;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
