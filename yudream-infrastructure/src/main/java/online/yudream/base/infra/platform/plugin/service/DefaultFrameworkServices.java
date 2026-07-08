package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.file.service.ObjectStorage;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import online.yudream.base.domain.platform.document.service.WordTemplateRenderer;
import online.yudream.base.domain.platform.document.valobj.RenderedDocument;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.document.PluginRenderedDocument;
import online.yudream.base.plugin.spi.system.document.PluginWordTemplateService;
import online.yudream.base.plugin.spi.system.security.PluginSecurityService;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.spi.system.storage.PluginFileStore;
import online.yudream.base.plugin.spi.system.user.PluginUserService;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class DefaultFrameworkServices implements FrameworkServices {

    private final PluginUserService pluginUserService;
    private final PluginSecurityService pluginSecurityService;
    private final MongoTemplate mongoTemplate;
    private final ObjectStorage objectStorage;
    private final WordTemplateRenderer wordTemplateRenderer;
    private final SettingRepo settingRepo;
    private final Environment environment;
    private final PluginExtensionRegistry pluginExtensionRegistry;
    private final ConcurrentMap<String, PluginDocumentStore> documentStores = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PluginFileStore> fileStores = new ConcurrentHashMap<>();

    @Override
    public PluginUserService users() {
        return pluginUserService;
    }

    @Override
    public PluginSecurityService security() {
        return pluginSecurityService;
    }

    @Override
    public PluginWordTemplateService wordTemplates() {
        return this::renderWordTemplate;
    }

    @Override
    public PluginDocumentStore documents(String pluginCode) {
        return documentStores.computeIfAbsent(pluginCode, code -> new MongoPluginDocumentStore(code, mongoTemplate));
    }

    @Override
    public PluginFileStore files(String pluginCode) {
        return fileStores.computeIfAbsent(pluginCode, code -> new ObjectStoragePluginFileStore(code, objectStorage));
    }

    @Override
    public Optional<String> setting(String key) {
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }
        return settingRepo.findByKey(key)
                .map(Setting::getValue)
                .filter(StringUtils::hasText)
                .or(() -> Optional.ofNullable(environment.getProperty(key)).filter(StringUtils::hasText));
    }

    @Override
    public <T> Optional<T> extension(String pluginCode, Class<T> type) {
        return pluginExtensionRegistry.find(pluginCode, type);
    }

    @Override
    public <T> List<T> extensions(Class<T> type) {
        return pluginExtensionRegistry.findAll(type);
    }

    private PluginRenderedDocument renderWordTemplate(byte[] templateContent, Map<String, Object> data) {
        if (templateContent == null || templateContent.length == 0) {
            throw new IllegalArgumentException("Word 模板内容不能为空");
        }
        RenderedDocument rendered = wordTemplateRenderer.render(new ByteArrayInputStream(templateContent), data);
        return new PluginRenderedDocument(rendered.content(), rendered.contentType());
    }
}
