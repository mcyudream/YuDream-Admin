package online.yudream.base.plugin.spi.system;

import online.yudream.base.plugin.spi.system.security.PluginSecurityService;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.spi.system.storage.PluginFileStore;
import online.yudream.base.plugin.spi.system.user.PluginUserService;

public interface FrameworkServices {

    PluginUserService users();

    PluginSecurityService security();

    PluginDocumentStore documents(String pluginCode);

    PluginFileStore files(String pluginCode);
}
