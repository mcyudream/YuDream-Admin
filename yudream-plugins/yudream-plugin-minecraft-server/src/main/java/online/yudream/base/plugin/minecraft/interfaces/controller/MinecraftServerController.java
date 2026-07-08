package online.yudream.base.plugin.minecraft.interfaces.controller;

import online.yudream.base.plugin.minecraft.bootstrap.MinecraftServerPlugin;
import online.yudream.base.plugin.minecraft.interfaces.http.MinecraftServerHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

public class MinecraftServerController {

    private final MinecraftServerHttpFacade http;

    public MinecraftServerController(MinecraftServerHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/servers", permission = MinecraftServerPlugin.VIEW_PERMISSION)
    public PluginHttpResponse list(PluginHttpRequest request) {
        return http.list(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/servers/{serverId}", permission = MinecraftServerPlugin.VIEW_PERMISSION)
    public PluginHttpResponse detail(PluginHttpRequest request) {
        return http.detail(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/servers", permission = MinecraftServerPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse save(PluginHttpRequest request) {
        return http.save(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/servers/{serverId}", permission = MinecraftServerPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse delete(PluginHttpRequest request) {
        return http.delete(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/servers/{serverId}/status/refresh", permission = MinecraftServerPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse refreshStatus(PluginHttpRequest request) {
        return http.refreshStatus(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/economy/status", permission = MinecraftServerPlugin.VIEW_PERMISSION)
    public PluginHttpResponse economyStatus(PluginHttpRequest request) {
        return http.economyStatus();
    }

    @PluginHttpEndpoint(method = "POST", path = "/servers/{serverId}/seasons/preview", permission = MinecraftServerPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse previewOpenSeason(PluginHttpRequest request) {
        return http.previewOpenSeason(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/servers/{serverId}/seasons/open", permission = MinecraftServerPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse openSeason(PluginHttpRequest request) {
        return http.openSeason(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/servers/{serverId}/operations", permission = MinecraftServerPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse operations(PluginHttpRequest request) {
        return http.operations(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/operations/{operationId}/rollback", permission = MinecraftServerPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse rollbackSeason(PluginHttpRequest request) {
        return http.rollbackSeason(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/servers/{serverId}/my-records", permission = MinecraftServerPlugin.VIEW_PERMISSION)
    public PluginHttpResponse myRecords(PluginHttpRequest request) {
        return http.myRecords(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/servers/{serverId}/players", permission = MinecraftServerPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse playerActivities(PluginHttpRequest request) {
        return http.playerActivities(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/servers/{serverId}/players/join", permission = MinecraftServerPlugin.REPORT_PERMISSION)
    public PluginHttpResponse playerJoin(PluginHttpRequest request) {
        return http.playerJoin(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/servers/{serverId}/players/quit", permission = MinecraftServerPlugin.REPORT_PERMISSION)
    public PluginHttpResponse playerQuit(PluginHttpRequest request) {
        return http.playerQuit(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/servers/{serverId}/players/afk/start", permission = MinecraftServerPlugin.REPORT_PERMISSION)
    public PluginHttpResponse playerAfkStart(PluginHttpRequest request) {
        return http.playerAfkStart(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/servers/{serverId}/players/afk/end", permission = MinecraftServerPlugin.REPORT_PERMISSION)
    public PluginHttpResponse playerAfkEnd(PluginHttpRequest request) {
        return http.playerAfkEnd(request);
    }
}
