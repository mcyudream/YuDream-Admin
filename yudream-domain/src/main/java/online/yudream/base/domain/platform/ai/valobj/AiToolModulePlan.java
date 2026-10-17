package online.yudream.base.domain.platform.ai.valobj;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单次运行的系统工具模块计划：由装配层按「当前注册的工具 + 工具开关设置」生成，
 * 是模块可见性与 load_tools 元工具的唯一数据源。
 * <p>
 * tools 只包含本次真正注册进工具集的工具（安全名为键）；被关闭模块的工具不会出现在其中。
 */
public record AiToolModulePlan(
        boolean systemEnabled,
        boolean onDemand,
        String metaToolName,
        Map<String, AiToolModules.ModuleMeta> modules,
        Map<String, ToolEntry> tools,
        Map<String, List<String>> moduleToolNames,
        Set<String> disabledModules
) {

    public record ToolEntry(
            String safeName,
            String toolName,
            String title,
            String description,
            String permissionCode,
            String risk,
            String moduleCode,
            boolean requiresConfirmation
    ) {
    }

    public AiToolModulePlan {
        metaToolName = metaToolName == null || metaToolName.isBlank() ? AiToolModules.LOAD_TOOLS : metaToolName;
        modules = modules == null ? Map.of() : Map.copyOf(modules);
        tools = tools == null ? Map.of() : Map.copyOf(tools);
        Map<String, List<String>> names = new LinkedHashMap<>();
        if (moduleToolNames != null) {
            moduleToolNames.forEach((code, value) -> names.put(code, value == null ? List.of() : List.copyOf(value)));
        }
        moduleToolNames = Map.copyOf(names);
        disabledModules = disabledModules == null ? Set.of() : Set.copyOf(disabledModules);
    }

    public boolean enabled() {
        return systemEnabled;
    }

    /** 关闭的模块（含总开关关闭时的全部模块）。 */
    public boolean moduleAllowed(String moduleCode) {
        if (!systemEnabled || moduleCode == null || moduleCode.isBlank()) {
            return false;
        }
        return !disabledModules.contains(moduleCode.trim());
    }

    public boolean toolAllowed(String safeName) {
        ToolEntry entry = safeName == null ? null : tools.get(safeName);
        return entry != null && moduleAllowed(entry.moduleCode());
    }

    public AiToolModules.ModuleMeta module(String moduleCode) {
        if (moduleCode == null) {
            return null;
        }
        return modules.get(moduleCode.trim());
    }

    public List<ToolEntry> moduleEntries(String moduleCode) {
        List<String> names = moduleCode == null ? null : moduleToolNames.get(moduleCode.trim());
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<ToolEntry> entries = new java.util.ArrayList<>(names.size());
        for (String name : names) {
            ToolEntry entry = tools.get(name);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return List.copyOf(entries);
    }

    /** 是否存在需要按需加载的模块（存在非常驻且已注册、未被关闭的模块）。 */
    public boolean onDemandActive() {
        if (!systemEnabled || !onDemand) {
            return false;
        }
        return moduleToolNames.keySet().stream().anyMatch(code -> {
            if (disabledModules.contains(code)) {
                return false;
            }
            AiToolModules.ModuleMeta meta = modules.get(code);
            return meta != null && !meta.alwaysOn();
        });
    }

    /** 生成运行期加载状态：常驻模块工具默认可见，其余需 load_tools 加载。 */
    public AiToolLoadState newLoadState() {
        return newLoadState(onDemandActive());
    }

    /**
     * 生成运行期加载状态；onDemand=false 时所有已注册工具直接可见
     * （用于审批/恢复续跑，保证原 turn 已规划的工具仍可执行）。
     */
    public AiToolLoadState newLoadState(boolean onDemandState) {
        Set<String> alwaysVisible = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> entry : moduleToolNames.entrySet()) {
            AiToolModules.ModuleMeta meta = modules.get(entry.getKey());
            if (meta != null && meta.alwaysOn()) {
                alwaysVisible.addAll(entry.getValue());
            }
        }
        return new AiToolLoadState(onDemandState && systemEnabled, metaToolName, alwaysVisible, moduleToolNames);
    }

    public Map<String, AiToolModules.ModuleMeta> modules() {
        return modules;
    }

    public Set<String> moduleCodes() {
        return moduleToolNames.keySet();
    }
}
