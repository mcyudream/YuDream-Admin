package online.yudream.base.plugin.spi.system.user;

public interface PluginQqBindingService {
    PluginQqBindingCode issue(Long userId);
    Long consume(String code);
}
