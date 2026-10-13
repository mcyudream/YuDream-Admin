package online.yudream.base.domain.system.security;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 权限码唯一，名称从真实登记值稳定选取，说明保留同码全部实际操作。 */
public final class PermissionCatalogNormalizer {

    private static final Map<String, List<String>> ACTION_NAMES = Map.ofEntries(
            Map.entry("view", List.of("查看")), Map.entry("export", List.of("导出")), Map.entry("download", List.of("下载")),
            Map.entry("create", List.of("新增", "创建", "新建")), Map.entry("edit", List.of("编辑")),
            Map.entry("import", List.of("导入")), Map.entry("upload", List.of("上传")), Map.entry("use", List.of("使用")),
            Map.entry("generate", List.of("生成")), Map.entry("publish", List.of("发布")), Map.entry("connect", List.of("连接")),
            Map.entry("send", List.of("发送")), Map.entry("test", List.of("测试")), Map.entry("run", List.of("执行")),
            Map.entry("invoke", List.of("调用")), Map.entry("execute", List.of("执行")), Map.entry("enable", List.of("启用")),
            Map.entry("disable", List.of("停用", "禁用")), Map.entry("assign", List.of("分配")),
            Map.entry("dataset", List.of("数据集")), Map.entry("report", List.of("报表")), Map.entry("accept", List.of("审核")),
            Map.entry("manage", List.of("管理")), Map.entry("config", List.of("配置")), Map.entry("delete", List.of("删除")),
            Map.entry("kickout", List.of("踢出")), Map.entry("revoke", List.of("吊销", "撤销")), Map.entry("impersonate", List.of("伪装")));

    private PermissionCatalogNormalizer() {
    }

    public static List<PermissionMeta> normalize(Collection<PermissionMeta> metadata) {
        if (metadata == null) {
            return List.of();
        }
        Map<String, List<PermissionMeta>> groups = metadata.stream().filter(meta -> meta != null && text(meta.code()))
                .collect(Collectors.groupingBy(PermissionMeta::code, TreeMap::new, Collectors.toList()));
        return groups.entrySet().stream().map(entry -> merge(entry.getKey(), entry.getValue())).toList();
    }

    public static String actionName(String code) {
        List<String> names = actionNames(code);
        return names.isEmpty() ? null : names.getFirst();
    }

    private static PermissionMeta merge(String code, List<PermissionMeta> values) {
        List<String> realNames = values.stream().map(PermissionMeta::name).filter(PermissionCatalogNormalizer::text)
                .map(String::trim).filter(name -> !name.equals(code)).toList();
        String name = select(realNames, code, candidate -> actionRank(code, candidate));
        List<String> realModules = values.stream().map(PermissionMeta::module).filter(PermissionCatalogNormalizer::text)
                .map(String::trim).filter(module -> !"default".equalsIgnoreCase(module)).toList();
        String module = select(realModules, "default", candidate -> 0);
        TreeSet<String> descriptions = new TreeSet<>();
        for (PermissionMeta value : values) {
            String description = text(value.desc()) ? value.desc().trim() : "";
            String declaredName = text(value.name()) ? value.name().trim() : "";
            if (text(declaredName) && !declaredName.equals(code) && !declaredName.equals(name)
                    && !description.contains(declaredName)) {
                description = description.isBlank() ? declaredName : declaredName + "：" + description;
            }
            if (!description.isBlank()) {
                descriptions.add(description);
            }
        }
        return new PermissionMeta(code, name, module, String.join("；", descriptions));
    }

    private static String select(List<String> candidates, String fallback, Function<String, Integer> rank) {
        Map<String, Long> frequency = candidates.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return frequency.keySet().stream().min(Comparator.comparing(rank)
                .thenComparing(candidate -> -frequency.get(candidate)).thenComparingInt(String::length)
                .thenComparing(Comparator.naturalOrder())).orElse(fallback);
    }

    private static int actionRank(String code, String name) {
        List<String> actionNames = actionNames(code);
        if (actionNames.stream().anyMatch(name::startsWith)) {
            return 0;
        }
        return actionNames.stream().anyMatch(name::contains) ? 1 : 2;
    }

    private static List<String> actionNames(String code) {
        if (!text(code) || !code.contains(":")) {
            return List.of();
        }
        String action = code.substring(code.lastIndexOf(':') + 1);
        if (action.matches("assign-[a-z][a-z0-9]*(?:-[a-z0-9]+)*")) {
            action = "assign";
        }
        return ACTION_NAMES.getOrDefault(action, List.of());
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }
}
