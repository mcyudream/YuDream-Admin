package online.yudream.base.plugin.spi.system;

import online.yudream.base.plugin.spi.system.security.PluginSecurityService;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.spi.system.storage.PluginFileStore;
import online.yudream.base.plugin.spi.system.user.PluginUserService;

import java.util.List;
import java.util.Optional;

public interface FrameworkServices {

    PluginUserService users();

    PluginSecurityService security();

    PluginDocumentStore documents(String pluginCode);

    PluginFileStore files(String pluginCode);

    <T> Optional<T> extension(String pluginCode, Class<T> type);

    <T> List<T> extensions(Class<T> type);
}
