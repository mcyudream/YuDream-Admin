package online.yudream.base.infra.platform.plugin;

import online.yudream.base.infra.platform.plugin.service.PluginTemplateRenderFrameworkService;
import online.yudream.base.plugin.spi.system.render.PluginRenderService;
import online.yudream.base.plugin.spi.system.render.PluginRenderedImage;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginTemplateRenderFrameworkServiceTest {

    @Test
    void rendersTemplateFromPluginClassLoaderAndPassesSelector() throws Exception {
        URL resourceRoot = getClass().getResource("/plugin-template-test/");
        assertThat(resourceRoot).isNotNull();
        AtomicReference<String> renderedHtml = new AtomicReference<>();
        AtomicReference<String> renderedSelector = new AtomicReference<>();
        PluginRenderService renderService = capturingRenderService(renderedHtml, renderedSelector);

        try (URLClassLoader pluginClassLoader = new URLClassLoader(new URL[]{resourceRoot}, getClass().getClassLoader())) {
            PluginTemplateRenderFrameworkService service = new PluginTemplateRenderFrameworkService(pluginClassLoader, renderService);
            PluginRenderedImage image = service.render("card", Map.of("name", "<Admin>"), "#card")
                    .toCompletableFuture().join();

            assertThat(renderedHtml.get()).contains("&lt;Admin&gt;").doesNotContain("th:text");
            assertThat(renderedSelector.get()).isEqualTo("#card");
            assertThat(new String(image.content(), StandardCharsets.UTF_8)).isEqualTo("rendered");
            assertThatThrownBy(() -> service.render("../card", Map.of(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("模板名称无效");
            assertThatThrownBy(() -> service.render("host-only", Map.of(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("模板不存在");
        }
    }

    private PluginRenderService capturingRenderService(AtomicReference<String> html, AtomicReference<String> selector) {
        return new PluginRenderService() {
            @Override
            public CompletionStage<PluginRenderedImage> html(String content) {
                return html(content, null);
            }

            @Override
            public CompletionStage<PluginRenderedImage> html(String content, String targetSelector) {
                html.set(content);
                selector.set(targetSelector);
                return CompletableFuture.completedFuture(new PluginRenderedImage(
                        "image/png", "rendered".getBytes(StandardCharsets.UTF_8), 320, 120));
            }

            @Override
            public CompletionStage<PluginRenderedImage> markdown(String markdown) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletionStage<PluginRenderedImage> url(String url) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
