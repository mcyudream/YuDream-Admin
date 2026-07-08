package online.yudream.base.plugin.activityproof.interfaces.controller;

import online.yudream.base.plugin.activityproof.bootstrap.MinecraftActivityProofPlugin;
import online.yudream.base.plugin.activityproof.interfaces.http.ActivityProofHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

public class ActivityProofController {

    private final ActivityProofHttpFacade http;

    public ActivityProofController(ActivityProofHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/status", permission = MinecraftActivityProofPlugin.VIEW_PERMISSION)
    public PluginHttpResponse status() {
        return http.status();
    }

    @PluginHttpEndpoint(method = "GET", path = "/servers", permission = MinecraftActivityProofPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse servers() {
        return http.servers();
    }

    @PluginHttpEndpoint(method = "GET", path = "/settings", permission = MinecraftActivityProofPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse settings() {
        return http.settings();
    }

    @PluginHttpEndpoint(method = "PUT", path = "/settings", permission = MinecraftActivityProofPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse saveSettings(PluginHttpRequest request) {
        return http.saveSettings(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/template", permission = MinecraftActivityProofPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse uploadTemplate(PluginHttpRequest request) {
        return http.uploadTemplate(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/mappings", permission = MinecraftActivityProofPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse mappings(PluginHttpRequest request) {
        return http.mappings(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/mappings", permission = MinecraftActivityProofPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse saveMapping(PluginHttpRequest request) {
        return http.saveMapping(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/mappings/{id}", permission = MinecraftActivityProofPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse deleteMapping(PluginHttpRequest request) {
        return http.deleteMapping(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/participants", permission = MinecraftActivityProofPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse participants(PluginHttpRequest request) {
        return http.participants(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/exports", permission = MinecraftActivityProofPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse export(PluginHttpRequest request) {
        return http.export(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/exports", permission = MinecraftActivityProofPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse exports(PluginHttpRequest request) {
        return http.exports(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/exports/{id}/download", permission = MinecraftActivityProofPlugin.MANAGE_PERMISSION, wrapResult = false)
    public PluginHttpResponse download(PluginHttpRequest request) {
        return http.download(request);
    }
}
