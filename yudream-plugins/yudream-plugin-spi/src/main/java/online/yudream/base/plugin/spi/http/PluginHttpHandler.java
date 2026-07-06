package online.yudream.base.plugin.spi.http;

public interface PluginHttpHandler {

    PluginHttpResponse handle(PluginHttpRequest request);
}
