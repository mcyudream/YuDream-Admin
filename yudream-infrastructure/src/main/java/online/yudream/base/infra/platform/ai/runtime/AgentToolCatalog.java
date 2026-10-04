package online.yudream.base.infra.platform.ai.runtime;

import java.util.Map;

/**
 * RuntimeContext 中的工具目录：safe 工具名 → 平台权限码，以及本次调用是否允许工具。
 * PerCallToolFilterMiddleware 据此在推理前过滤模型可见的工具 schema。
 */
public record AgentToolCatalog(boolean enabled, Map<String, String> safeNameToPermissionCode) {

    public static AgentToolCatalog disabled() {
        return new AgentToolCatalog(false, Map.of());
    }

    public Map<String, String> permissions() {
        return safeNameToPermissionCode == null ? Map.of() : safeNameToPermissionCode;
    }
}
