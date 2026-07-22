package online.yudream.base.plugin.spi.system;

import online.yudream.base.plugin.spi.system.security.PluginSecurityService;
import online.yudream.base.plugin.spi.system.document.PluginWordTemplateService;
import online.yudream.base.plugin.spi.system.mail.PluginMailService;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.spi.system.storage.PluginFileStore;
import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;
import online.yudream.base.plugin.spi.system.user.PluginUserService;
import online.yudream.base.plugin.spi.system.user.PluginQqBindingService;
import online.yudream.base.plugin.spi.system.command.PluginCommandService;
import online.yudream.base.plugin.spi.system.ai.PluginAiService;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingService;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingRawService;
import online.yudream.base.plugin.spi.system.render.PluginRenderService;
import online.yudream.base.plugin.spi.system.secret.PluginSecretStore;

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

    default PluginSecretStore secrets(String pluginCode) {
        return new PluginSecretStore() {
            private UnsupportedOperationException unavailable() {
                return new UnsupportedOperationException("Plugin secret storage is unavailable");
            }
            @Override public void put(String key, byte[] secret) { throw unavailable(); }
            @Override public Optional<byte[]> get(String key) { throw unavailable(); }
            @Override public boolean delete(String key) { throw unavailable(); }
        };
    }

    PluginMessagingService messaging();

    PluginMessagingRawService messagingRaw();

    PluginRenderService render();

    /**
     * 读取平台文件存储（/api/files 上传）中的文件，按文件 ID 只读访问。
     * 用于插件消费经平台通用上传接口进入的大文件（如存档压缩包）。
     */
    Optional<PluginStoredFile> platformFile(String fileId);

    Optional<String> setting(String key);

}
