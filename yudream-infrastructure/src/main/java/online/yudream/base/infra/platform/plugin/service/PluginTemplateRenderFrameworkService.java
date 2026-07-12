package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.plugin.spi.system.render.PluginRenderService;
import online.yudream.base.plugin.spi.system.render.PluginRenderedImage;
import online.yudream.base.plugin.spi.system.render.PluginTemplateRenderService;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class PluginTemplateRenderFrameworkService implements PluginTemplateRenderService {

    private final URLClassLoader pluginClassLoader;
    private final PluginRenderService renderService;
    private final SpringTemplateEngine templateEngine;

    public PluginTemplateRenderFrameworkService(URLClassLoader pluginClassLoader, PluginRenderService renderService) {
        this.pluginClassLoader = pluginClassLoader;
        this.renderService = renderService;
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver(pluginResourcesOnly(pluginClassLoader));
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        this.templateEngine = new SpringTemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);
    }

    @Override
    public CompletionStage<PluginRenderedImage> render(String templateName, Map<String, Object> variables, String selector) {
        String normalizedName = normalizeTemplateName(templateName);
        String resourcePath = "templates/" + normalizedName + ".html";
        if (pluginClassLoader.findResource(resourcePath) == null) {
            throw new IllegalArgumentException("插件模板不存在: " + resourcePath);
        }
        Context context = new Context();
        context.setVariables(variables == null ? Map.of() : new LinkedHashMap<>(variables));
        String html = templateEngine.process(normalizedName, context);
        return selector == null || selector.isBlank()
                ? renderService.html(html)
                : renderService.html(html, selector.trim());
    }

    private String normalizeTemplateName(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException("插件模板名称不能为空");
        }
        String normalized = templateName.trim();
        if (normalized.startsWith("/") || normalized.endsWith("/") || normalized.contains("..")
                || normalized.contains("\\") || !normalized.matches("[A-Za-z0-9/_-]+")) {
            throw new IllegalArgumentException("插件模板名称无效: " + templateName);
        }
        return normalized;
    }

    private ClassLoader pluginResourcesOnly(URLClassLoader source) {
        return new ClassLoader(null) {
            @Override
            public URL getResource(String name) {
                return source.findResource(name);
            }

            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                return source.findResources(name);
            }
        };
    }
}
