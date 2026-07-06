package online.yudream.base.application.system.dashboard.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginDashboardCardInfo;
import online.yudream.base.domain.system.dashboard.valobj.DashboardCardDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardCardRegistry {

    private static final String DATAVIZ_CAPABILITY_CODE = "dataviz";

    private final PluginRuntimeGateway pluginRuntimeGateway;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final List<CapabilityProvider> capabilityProviders;

    public List<DashboardCardDefinition> allCards() {
        Map<String, DashboardCardDefinition> cards = new LinkedHashMap<>();
        builtinCards().forEach(card -> cards.put(card.code(), card));
        pluginRuntimeGateway.dashboardCards().stream()
                .map(this::fromPlugin)
                .forEach(card -> cards.putIfAbsent(card.code(), card));
        return cards.values().stream()
                .sorted(Comparator.comparingInt(DashboardCardDefinition::sort).thenComparing(DashboardCardDefinition::code))
                .toList();
    }

    private List<DashboardCardDefinition> builtinCards() {
        List<DashboardCardDefinition> cards = new ArrayList<>();
        cards.add(new DashboardCardDefinition(
                "system.profile",
                "欢迎回来",
                "展示当前登录身份和工作上下文。",
                "i-ri:user-smile-line",
                "个人",
                "SYSTEM",
                null,
                null,
                "PROFILE_SUMMARY",
                null,
                null,
                "blue",
                4,
                3,
                3,
                2,
                10
        ));
        cards.add(new DashboardCardDefinition(
                "system.quick-actions",
                "工作入口",
                "从当前可访问菜单中整理常用入口。",
                "i-ri:compass-3-line",
                "工作",
                "SYSTEM",
                null,
                null,
                "QUICK_ACTIONS",
                null,
                null,
                "gray",
                8,
                3,
                4,
                2,
                20
        ));
        cards.add(new DashboardCardDefinition(
                "system.monitor",
                "运行监控",
                "展示在线会话和 Redis 运行状态。",
                "i-ri:pulse-line",
                "监控",
                "SYSTEM",
                null,
                "system:monitor:redis:view",
                "MONITOR_STATS",
                "/system/redis-monitor",
                null,
                "amber",
                4,
                3,
                3,
                2,
                30
        ));
        cards.add(new DashboardCardDefinition(
                "platform.capability",
                "平台能力",
                "展示可选平台能力的启用和健康概况。",
                "i-ri:apps-2-line",
                "平台",
                "SYSTEM",
                null,
                "platform:capability:view",
                "CAPABILITY_STATS",
                "/platform/capability",
                null,
                "cyan",
                4,
                3,
                3,
                2,
                40
        ));
        cards.add(new DashboardCardDefinition(
                "platform.plugins",
                "插件管理",
                "展示插件加载、启用和异常概况。",
                "i-ri:plug-line",
                "平台",
                "SYSTEM",
                null,
                "platform:plugin:view",
                "PLUGIN_STATS",
                "/platform/plugin",
                null,
                "gray",
                4,
                3,
                3,
                2,
                50
        ));
        if (datavizEnabled()) {
            cards.add(new DashboardCardDefinition(
                    "platform.dataviz.capability-stats",
                    "能力分类统计",
                    "使用数据可视化能力展示平台能力类型分布。",
                    "i-ri:bar-chart-2-line",
                    "平台",
                    "SYSTEM",
                    null,
                    "platform:dataviz:dataset",
                    "DATAVIZ_CAPABILITY_STATS",
                    "/platform/capability",
                    null,
                    "cyan",
                    4,
                    3,
                    3,
                    2,
                    60
            ));
            cards.add(new DashboardCardDefinition(
                    "system.dataviz.user-registration",
                    "用户注册",
                    "按日期统计最近 8 天管理端用户注册趋势。",
                    "i-ri:user-add-line",
                    "系统",
                    "SYSTEM",
                    null,
                    "platform:dataviz:dataset",
                    "DATAVIZ_USER_REGISTRATION",
                    "/system/user",
                    null,
                    "blue",
                    8,
                    4,
                    4,
                    3,
                    61
            ));
            cards.add(new DashboardCardDefinition(
                    "system.dataviz.dept-created",
                    "部门新增",
                    "按日期统计最近 8 天组织部门新增趋势。",
                    "i-ri:organization-chart",
                    "系统",
                    "SYSTEM",
                    null,
                    "platform:dataviz:dataset",
                    "DATAVIZ_DEPT_CREATED",
                    "/system/dept",
                    null,
                    "gray",
                    4,
                    4,
                    3,
                    3,
                    62
            ));
            cards.add(new DashboardCardDefinition(
                    "system.dataviz.log-activity",
                    "接口调用",
                    "按日期统计最近 8 天接口访问日志趋势。",
                    "i-ri:file-chart-line",
                    "监控",
                    "SYSTEM",
                    null,
                    "platform:dataviz:dataset",
                    "DATAVIZ_LOG_ACTIVITY",
                    "/system/api-log",
                    null,
                    "amber",
                    4,
                    4,
                    3,
                    3,
                    63
            ));
        }
        return cards;
    }

    private boolean datavizEnabled() {
        boolean providerAvailable = capabilityProviders.stream()
                .map(CapabilityProvider::descriptor)
                .anyMatch(descriptor -> DATAVIZ_CAPABILITY_CODE.equals(descriptor.code()));
        if (!providerAvailable) {
            return false;
        }
        return capabilityModuleRepo.findByCode(DATAVIZ_CAPABILITY_CODE)
                .map(CapabilityModule::enabled)
                .orElse(false);
    }

    private DashboardCardDefinition fromPlugin(PluginDashboardCardInfo card) {
        return new DashboardCardDefinition(
                card.pluginCode() + "." + card.code(),
                card.title(),
                card.description(),
                card.icon(),
                card.category(),
                "PLUGIN",
                card.pluginCode(),
                card.permission(),
                card.component(),
                card.actionPath(),
                card.dragPayloadTemplate(),
                card.tone(),
                card.defaultW(),
                card.defaultH(),
                card.minW(),
                card.minH(),
                card.sort()
        );
    }
}
