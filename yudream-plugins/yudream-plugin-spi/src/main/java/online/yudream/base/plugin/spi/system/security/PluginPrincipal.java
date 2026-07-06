package online.yudream.base.plugin.spi.system.security;

import java.util.List;

public record PluginPrincipal(
        Long userId,
        List<String> permissions
) {
    public PluginPrincipal {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains("*") || permissions.contains(permission);
    }
}
