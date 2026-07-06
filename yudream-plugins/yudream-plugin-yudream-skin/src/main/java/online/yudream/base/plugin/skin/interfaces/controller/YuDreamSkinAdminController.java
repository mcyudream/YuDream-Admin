package online.yudream.base.plugin.skin.interfaces.controller;

import online.yudream.base.plugin.skin.bootstrap.YuDreamSkinPlugin;
import online.yudream.base.plugin.skin.interfaces.http.YuDreamSkinHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

public class YuDreamSkinAdminController {

    private final YuDreamSkinHttpFacade http;

    public YuDreamSkinAdminController(YuDreamSkinHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/players", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse players(PluginHttpRequest request) {
        return http.players(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/players", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse createPlayer(PluginHttpRequest request) {
        return http.createPlayer(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/players/{name}", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse player(PluginHttpRequest request) {
        return http.player(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/players/{name}/name", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse renamePlayer(PluginHttpRequest request) {
        return http.renamePlayer(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/players/{name}/textures", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse assignTextures(PluginHttpRequest request) {
        return http.assignTextures(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/players/{name}", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse deletePlayer(PluginHttpRequest request) {
        return http.deletePlayer(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/textures", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse textures(PluginHttpRequest request) {
        return http.adminTextures(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/textures", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse uploadTexture(PluginHttpRequest request) {
        return http.uploadTexture(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/textures/{hash}", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse deleteTexture(PluginHttpRequest request) {
        return http.deleteTexture(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/closet", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse closet(PluginHttpRequest request) {
        return http.closet(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/closet", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse saveClosetItem(PluginHttpRequest request) {
        return http.saveClosetItem(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/closet/{id}", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse renameClosetItem(PluginHttpRequest request) {
        return http.renameClosetItem(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/closet/{id}", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse deleteClosetItem(PluginHttpRequest request) {
        return http.deleteClosetItem(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/settings", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse settings() {
        return http.settings();
    }

    @PluginHttpEndpoint(method = "PUT", path = "/settings", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse saveSettings(PluginHttpRequest request) {
        return http.saveSettings(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/migration/blessing-skin", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse migrate(PluginHttpRequest request) {
        return http.migrate(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/migration/blessing-skin/status", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse migrationStatus() {
        return http.migrationStatus();
    }

    @PluginHttpEndpoint(method = "GET", path = "/migration/blessing-skin/events", permission = YuDreamSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse migrationEvents() {
        return http.migrationEvents();
    }
}
