package online.yudream.base.plugin.blessing.interfaces.controller;

import online.yudream.base.plugin.blessing.bootstrap.BlessingSkinPlugin;
import online.yudream.base.plugin.blessing.interfaces.http.BlessingSkinHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

public class BlessingSkinPublicController {

    private final BlessingSkinHttpFacade http;

    public BlessingSkinPublicController(BlessingSkinHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/status", permission = BlessingSkinPlugin.VIEW_PERMISSION)
    public PluginHttpResponse status() {
        return http.status();
    }

    @PluginHttpEndpoint(method = "GET", path = "/textures", permission = BlessingSkinPlugin.VIEW_PERMISSION)
    public PluginHttpResponse textures(PluginHttpRequest request) {
        return http.textures(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/textures/{hash}", wrapResult = false)
    public PluginHttpResponse textureContent(PluginHttpRequest request) {
        return http.textureContent(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/csl/{name}", wrapResult = false)
    public PluginHttpResponse customSkinProfile(PluginHttpRequest request) {
        return http.customSkinProfile(request);
    }
}
