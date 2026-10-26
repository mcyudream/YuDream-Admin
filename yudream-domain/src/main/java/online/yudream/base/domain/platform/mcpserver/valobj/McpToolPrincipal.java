package online.yudream.base.domain.platform.mcpserver.valobj;

import java.util.List;

/**
 * MCP 调用者身份（来自 API Key / OAuth / 登录会话解析出的安全主体）。
 * 权限为该主体可见的权限码快照，含 "*" 表示全量。
 */
public record McpToolPrincipal(Long userId, List<String> permissions) {
    public McpToolPrincipal {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    public boolean hasPermission(String permission) {
        return permission == null || permission.isBlank()
                || permissions.contains("*") || permissions.contains(permission);
    }
}
