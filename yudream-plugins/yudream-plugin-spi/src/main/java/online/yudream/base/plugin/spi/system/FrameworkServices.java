package online.yudream.base.plugin.spi.system;

import online.yudream.base.plugin.spi.system.security.PluginSecurityService;
import online.yudream.base.plugin.spi.system.document.PluginWordTemplateService;
import online.yudream.base.plugin.spi.system.mail.PluginMailService;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.spi.system.storage.PluginFileStore;
import online.yudream.base.plugin.spi.system.user.PluginUserService;
import online.yudream.base.plugin.spi.system.user.PluginQqBindingService;
import online.yudream.base.plugin.spi.system.command.PluginCommandService;
import online.yudream.base.plugin.spi.system.ai.PluginAiService;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingService;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingRawService;
import online.yudream.base.plugin.spi.system.render.PluginRenderService;

import java.util.Optional;

public interface FrameworkServices {

    PluginUserService users();

    PluginQqBindingService qqBindings();

    PluginCommandService commands();

    PluginAiService ai();

    PluginSecurityService security();

    PluginMailService mail();

    PluginWordTemplateService wordTemplates();

    PluginDocumentStore documents(String pluginCode);

    PluginFileStore files(String pluginCode);

    PluginMessagingService messaging();

    PluginMessagingRawService messagingRaw();

    PluginRenderService render();

    Optional<String> setting(String key);

}
