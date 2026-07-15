package online.yudream.base.domain.platform.agent.service;

import java.util.List;

public interface AgentPermissionGateway {
    boolean hasPermission(String permissionCode);

    default boolean hasPermission(
            String permissionCode,
            List<String> permissionCodes,
            boolean explicitContext
    ) {
        if (!explicitContext) {
            return hasPermission(permissionCode);
        }
        if (permissionCode == null || permissionCode.isBlank()) {
            return true;
        }
        return permissionCodes != null
                && (permissionCodes.contains("*") || permissionCodes.contains(permissionCode));
    }
}
