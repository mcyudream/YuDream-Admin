package online.yudream.base.plugin.spi.system;

import online.yudream.base.plugin.spi.system.security.PluginSecurityService;
import online.yudream.base.plugin.spi.system.document.PluginWordTemplateService;
import online.yudream.base.plugin.spi.system.mail.PluginMailService;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.spi.system.storage.PluginFileStore;
import online.yudream.base.plugin.spi.system.user.PluginUserService;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingService;
import online.yudream.base.plugin.spi.system.messaging.PluginSatoriRawService;
import online.yudream.base.plugin.spi.system.render.PluginRenderService;

import java.util.List;
import java.util.Optional;

public interface FrameworkServices {

    PluginUserService users();

    PluginSecurityService security();

    PluginMailService mail();

    PluginWordTemplateService wordTemplates();

    PluginDocumentStore documents(String pluginCode);

    PluginFileStore files(String pluginCode);

    PluginMessagingService messaging();

    PluginSatoriRawService satoriRaw();

    PluginRenderService render();

    Optional<String> setting(String key);

    <T> Optional<T> extension(String pluginCode, Class<T> type);

    <T> List<T> extensions(Class<T> type);
}
