package online.yudream.base.domain.platform.plugin.service;

import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 插件依赖图领域服务：受影响闭包、停机/恢复排序与级联可恢复分类。
 *
 * <p>产品规则：plugin.yml 的 depend/softdepend 图必须无环；这里的 visiting 集合只是
 * 畸形描述符的安全网，避免排序时 StackOverflowError。
 */
public final class PluginDependencyGraph {

    private PluginDependencyGraph() {
    }

    /**
     * 目标及其全部（硬+软）传递依赖方闭包，按「叶子依赖方最先、目标最后」排序，
     * 停机按此顺序、恢复按反向顺序。
     */
    public static List<PluginModule> affectedClosure(String targetCode, Map<String, PluginModule> modulesByCode) {
        Set<String> affected = new LinkedHashSet<>();
        affected.add(targetCode);
        boolean changed;
        do {
            changed = false;
            for (PluginModule module : modulesByCode.values()) {
                if (!affected.contains(module.getCode()) && dependsOnAny(module, affected)) {
                    changed |= affected.add(module.getCode());
                }
            }
        } while (changed);
        Map<String, Integer> depths = new HashMap<>();
        return affected.stream()
                .map(modulesByCode::get)
                .filter(module -> module != null)
                .sorted(Comparator.<PluginModule>comparingInt(module ->
                                reverseDependencyDepth(module, modulesByCode, affected, new HashSet<>(), depths))
                        .thenComparing(PluginModule::getCode))
                .toList();
    }

    /**
     * 全量恢复顺序：提供方最先、依赖方最后（硬+软边都参与深度计算），同层按 code 字母序决胜。
     *
     * <p>启动恢复与整体重载必须用它而不是字母序：插件 ClassLoader 的软依赖查找链在消费方
     * 创建时一次性捕获，字母序会让消费方（如 mcpanel）先于提供方（如 minecraft-server）
     * 加载，运行期引用提供方 API 类会直接 NoClassDefFoundError。畸形描述符成环时由
     * visiting 兜底为同层，交由单插件恢复的重试/降级处理。
     */
    public static List<PluginModule> restoreOrder(Map<String, PluginModule> modulesByCode) {
        Set<String> all = new HashSet<>(modulesByCode.keySet());
        Map<String, Integer> depths = new HashMap<>();
        return modulesByCode.values().stream()
                .sorted(Comparator.<PluginModule>comparingInt(module ->
                                reverseDependencyDepth(module, modulesByCode, all, new HashSet<>(), depths)).reversed()
                        .thenComparing(PluginModule::getCode))
                .toList();
    }

    /**
     * 仅沿硬依赖边回溯的依赖方闭包（不含目标自身）：这些插件离开目标无法运行，
     * 级联停机后不能自动恢复，只能保持禁用；闭包之外的软依赖方可降级恢复。
     */
    public static Set<String> hardDependentClosure(String targetCode, Map<String, PluginModule> modulesByCode) {
        Set<String> closure = new HashSet<>();
        closure.add(targetCode);
        boolean changed;
        do {
            changed = false;
            for (PluginModule module : modulesByCode.values()) {
                if (closure.contains(module.getCode())) {
                    continue;
                }
                List<String> dependencies = module.getDependencies() == null ? List.of() : module.getDependencies();
                if (dependencies.stream().anyMatch(closure::contains)) {
                    changed |= closure.add(module.getCode());
                }
            }
        } while (changed);
        closure.remove(targetCode);
        return closure;
    }

    /** 直接依赖方编码（硬依赖在前、软依赖在后），供管理页弹窗展示。 */
    public static List<String> directDependentCodes(String targetCode, Map<String, PluginModule> modulesByCode) {
        List<String> hard = new ArrayList<>();
        List<String> soft = new ArrayList<>();
        for (PluginModule module : modulesByCode.values()) {
            if (targetCode.equals(module.getCode())) {
                continue;
            }
            List<String> dependencies = module.getDependencies() == null ? List.of() : module.getDependencies();
            List<String> softDependencies = module.getSoftDependencies() == null ? List.of() : module.getSoftDependencies();
            if (dependencies.contains(targetCode)) {
                hard.add(module.getCode());
            } else if (softDependencies.contains(targetCode)) {
                soft.add(module.getCode());
            }
        }
        List<String> result = new ArrayList<>(hard);
        result.addAll(soft);
        return result;
    }

    private static boolean dependsOnAny(PluginModule module, Set<String> codes) {
        List<String> dependencies = module.getDependencies() == null ? List.of() : module.getDependencies();
        List<String> softDependencies = module.getSoftDependencies() == null ? List.of() : module.getSoftDependencies();
        return dependencies.stream().anyMatch(codes::contains) || softDependencies.stream().anyMatch(codes::contains);
    }

    /** 反向依赖深度：依赖方叶子为 0，被依赖越多越深；目标最深、排最后。 */
    private static int reverseDependencyDepth(PluginModule module, Map<String, PluginModule> modulesByCode,
                                              Set<String> affected, Set<String> visiting, Map<String, Integer> memo) {
        String code = module.getCode();
        Integer cached = memo.get(code);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(code)) {
            return 0;
        }
        int depth = 0;
        for (PluginModule dependent : modulesByCode.values()) {
            if (affected.contains(dependent.getCode()) && dependsOn(dependent, code)) {
                depth = Math.max(depth, 1 + reverseDependencyDepth(dependent, modulesByCode, affected, visiting, memo));
            }
        }
        visiting.remove(code);
        memo.put(code, depth);
        return depth;
    }

    private static boolean dependsOn(PluginModule module, String code) {
        List<String> dependencies = module.getDependencies() == null ? List.of() : module.getDependencies();
        List<String> softDependencies = module.getSoftDependencies() == null ? List.of() : module.getSoftDependencies();
        return dependencies.contains(code) || softDependencies.contains(code);
    }
}
