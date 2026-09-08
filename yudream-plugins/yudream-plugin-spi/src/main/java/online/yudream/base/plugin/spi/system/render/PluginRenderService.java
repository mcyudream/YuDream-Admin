package online.yudream.base.plugin.spi.system.render;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface PluginRenderService {
    CompletionStage<PluginRenderedImage> html(String html);
    default CompletionStage<PluginRenderedImage> html(String html, String selector) {
        return html(html);
    }
    CompletionStage<PluginRenderedImage> markdown(String markdown);
    CompletionStage<PluginRenderedImage> url(String url);

    /**
     * Fetch a public HTTP(S) URL through the isolated headless browser and return the document HTML.
     * JavaScript is enabled so the page can finish like a real browser; the host encodes the URL as URL-safe Base64.
     */
    default CompletionStage<PluginRenderedPage> htmlFromUrl(String url) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("htmlFromUrl is unavailable"));
    }
}
