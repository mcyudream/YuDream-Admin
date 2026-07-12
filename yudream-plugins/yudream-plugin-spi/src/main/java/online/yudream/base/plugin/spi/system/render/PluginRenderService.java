package online.yudream.base.plugin.spi.system.render;

import java.util.concurrent.CompletionStage;

public interface PluginRenderService {
    CompletionStage<PluginRenderedImage> html(String html);
    default CompletionStage<PluginRenderedImage> html(String html, String selector) {
        return html(html);
    }
    CompletionStage<PluginRenderedImage> markdown(String markdown);
    CompletionStage<PluginRenderedImage> url(String url);
}
