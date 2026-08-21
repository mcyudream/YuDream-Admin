package online.yudream.base.domain.platform.plugin.valobj;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 两次运行时资产快照之间的差异，目前用于开发模式 RELOAD 生命周期事件，
 * 让开发者工具直接回答「这次重载多了什么、少了什么」。
 * 仅收录有变化的类别；全量相等时 {@link #entries()} 为空。
 */
public record PluginRuntimeAssetsDiff(List<Entry> entries) {

    public PluginRuntimeAssetsDiff {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** 单类别差异：category 与 PluginRuntimeAssets 字段同名（commands/httpEndpoints/...），added/removed 为该类别资产的稳定标识 */
    public record Entry(String category, List<String> added, List<String> removed) {
        public Entry {
            added = added == null ? List.of() : List.copyOf(added);
            removed = removed == null ? List.of() : List.copyOf(removed);
        }
    }

    public static PluginRuntimeAssetsDiff diff(PluginRuntimeAssets before, PluginRuntimeAssets after) {
        List<Entry> entries = new ArrayList<>();
        add(entries, "menus", before.menus(), after.menus(), PluginMenuAssetInfo::path);
        add(entries, "permissions", before.permissions(), after.permissions(), PluginPermissionInfo::code);
        add(entries, "capabilities", before.capabilities(), after.capabilities(), PluginCapabilityAssetInfo::code);
        add(entries, "dashboardCards", before.dashboardCards(), after.dashboardCards(), PluginDashboardCardInfo::code);
        add(entries, "frontendModules", before.frontendModules(), after.frontendModules(), PluginFrontendModuleInfo::moduleName);
        add(entries, "httpEndpoints", before.httpEndpoints(), after.httpEndpoints(),
                endpoint -> endpoint.method() + " " + endpoint.fullPath());
        add(entries, "commands", before.commands(), after.commands(), PluginCommandInfo::command);
        add(entries, "messageInteractions", before.messageInteractions(), after.messageInteractions(),
                interaction -> interaction.command() != null && !interaction.command().isBlank()
                        ? interaction.command() : interaction.kind());
        add(entries, "aiTools", before.aiTools(), after.aiTools(), PluginAiToolInfo::name);
        add(entries, "agents", before.agents(), after.agents(), PluginRuntimeAgentInfo::code);
        add(entries, "exposedServices", before.exposedServices(), after.exposedServices(), Function.identity());
        return new PluginRuntimeAssetsDiff(entries);
    }

    private static <T> void add(List<Entry> entries, String category, List<T> before, List<T> after,
                                Function<T, String> identity) {
        Set<String> beforeKeys = keys(before, identity);
        Set<String> afterKeys = keys(after, identity);
        List<String> added = afterKeys.stream().filter(key -> !beforeKeys.contains(key)).toList();
        List<String> removed = beforeKeys.stream().filter(key -> !afterKeys.contains(key)).toList();
        if (!added.isEmpty() || !removed.isEmpty()) {
            entries.add(new Entry(category, added, removed));
        }
    }

    private static <T> Set<String> keys(List<T> items, Function<T, String> identity) {
        Set<String> keys = new LinkedHashSet<>();
        for (T item : items) {
            String key = identity.apply(item);
            keys.add(key == null ? "" : key);
        }
        return keys;
    }
}
