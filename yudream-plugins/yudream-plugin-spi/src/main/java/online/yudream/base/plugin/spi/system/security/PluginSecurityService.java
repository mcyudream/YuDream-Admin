package online.yudream.base.plugin.spi.system.security;

public interface PluginSecurityService {

    boolean hasPermission(PluginPrincipal principal, String permission);

    void requirePermission(PluginPrincipal principal, String permission);
}
