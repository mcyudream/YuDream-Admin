package online.yudream.base.domain.platform.ai.valobj;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单次运行的工具按需加载状态：常驻模块的工具始终可见，其余模块需经 load_tools 加载。
 * 该对象在 Harness 运行装配时创建，随 RuntimeContext 传给工具过滤中间件与 load_tools 元工具，
 * 因此必须线程安全（模型推理与工具执行可能不在同一线程）。
 */
public final class AiToolLoadState {

    private final boolean onDemand;
    private final String metaToolName;
    private final Set<String> alwaysVisible;
    private final Map<String, List<String>> moduleToolNames;
    private final Set<String> loaded = ConcurrentHashMap.newKeySet();

    public AiToolLoadState(
            boolean onDemand,
            String metaToolName,
            Set<String> alwaysVisible,
            Map<String, List<String>> moduleToolNames
    ) {
        this.onDemand = onDemand;
        this.metaToolName = metaToolName;
        this.alwaysVisible = alwaysVisible == null ? Set.of() : Set.copyOf(alwaysVisible);
        Map<String, List<String>> modules = new LinkedHashMap<>();
        if (moduleToolNames != null) {
            moduleToolNames.forEach((code, names) -> modules.put(code, names == null ? List.of() : List.copyOf(names)));
        }
        this.moduleToolNames = Map.copyOf(modules);
    }

    /** 是否需要按需加载：存在非常驻模块时为 true，用于注入系统提示词提示。 */
    public boolean onDemand() {
        return onDemand;
    }

    public String metaToolName() {
        return metaToolName;
    }

    /** 模型侧安全名是否可见：常驻工具、元工具或已加载模块的工具。 */
    public boolean visible(String safeToolName) {
        if (safeToolName == null || safeToolName.isBlank()) {
            return false;
        }
        if (safeToolName.equals(metaToolName) || alwaysVisible.contains(safeToolName)) {
            return true;
        }
        if (!onDemand) {
            return true;
        }
        for (String module : loaded) {
            List<String> names = moduleToolNames.get(module);
            if (names != null && names.contains(safeToolName)) {
                return true;
            }
        }
        return false;
    }

    /** 加载模块；模块不存在时返回 false。 */
    public boolean load(String moduleCode) {
        if (moduleCode == null || moduleCode.isBlank()) {
            return false;
        }
        String code = moduleCode.trim();
        if (!moduleToolNames.containsKey(code)) {
            return false;
        }
        loaded.add(code);
        return true;
    }

    public boolean loaded(String moduleCode) {
        return moduleCode != null && loaded.contains(moduleCode.trim());
    }

    public Set<String> loadedModules() {
        return Set.copyOf(new LinkedHashSet<>(loaded));
    }

    public List<String> moduleToolNames(String moduleCode) {
        List<String> names = moduleCode == null ? null : moduleToolNames.get(moduleCode.trim());
        return names == null ? List.of() : names;
    }

    public Set<String> moduleCodes() {
        return moduleToolNames.keySet();
    }

    public Map<String, List<String>> moduleToolNames() {
        return moduleToolNames;
    }

    public Set<String> alwaysVisibleNames() {
        return alwaysVisible;
    }
}
