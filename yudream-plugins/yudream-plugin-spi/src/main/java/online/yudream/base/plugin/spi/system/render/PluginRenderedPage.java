package online.yudream.base.plugin.spi.system.render;

/**
 * Isolated headless-browser document fetch result.
 * {@code html} is the document after a public HTTP(S) navigation with JavaScript enabled
 * and same-site script/style/XHR allowed. Images, media and cross-site requests stay blocked.
 */
public record PluginRenderedPage(String html, String finalUrl) {
}
