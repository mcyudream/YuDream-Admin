package online.yudream.base.infra.platform.ai.runtime;

import java.util.Map;
import java.util.Set;

/**
 * RuntimeContext 中的工具目录：safe 工具名 → 平台权限码，本次调用是否允许工具，
 * 以及可选的平台安全名白名单（如 wiki 问答只暴露 wiki.search）。
 * PerCallToolFilterMiddleware 据此在推理前过滤模型可见的工具 schema。
 */
public record AgentToolCatalog(
        boolean enabled,
        Map<String, String> safeNameToPermissionCode,
        Set<String> allowedNames
) {
    public static AgentToolCatalog disabled() {
        return new AgentToolCatalog(false, Map.of(), Set.of());
    }

    public AgentToolCatalog(boolean enabled, Map<String, String> safeNameToPermissionCode) {
        this(enabled, safeNameToPermissionCode, Set.of());
    }

    public Map<String, String> permissions() {
        return safeNameToPermissionCode == null ? Map.of() : safeNameToPermissionCode;
    }

    public Set<String> allowed() {
        return allowedNames == null ? Set.of() : allowedNames;
    }
}
