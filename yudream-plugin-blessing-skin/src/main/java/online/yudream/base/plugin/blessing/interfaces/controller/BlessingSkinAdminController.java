package online.yudream.base.plugin.blessing.interfaces.controller;

import online.yudream.base.plugin.blessing.bootstrap.BlessingSkinPlugin;
import online.yudream.base.plugin.blessing.interfaces.http.BlessingSkinHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

public class BlessingSkinAdminController {

    private final BlessingSkinHttpFacade http;

    public BlessingSkinAdminController(BlessingSkinHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/users", permission = BlessingSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse users(PluginHttpRequest request) {
        return http.users(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/users", permission = BlessingSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse createUser(PluginHttpRequest request) {
        return http.createUser(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/players", permission = BlessingSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse players(PluginHttpRequest request) {
        return http.players(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/players", permission = BlessingSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse createPlayer(PluginHttpRequest request) {
        return http.createPlayer(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/players/{name}", permission = BlessingSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse player(PluginHttpRequest request) {
        return http.player(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/players/{name}/name", permission = BlessingSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse renamePlayer(PluginHttpRequest request) {
        return http.renamePlayer(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/players/{name}/textures", permission = BlessingSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse assignTextures(PluginHttpRequest request) {
        return http.assignTextures(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/players/{name}", permission = BlessingSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse deletePlayer(PluginHttpRequest request) {
        return http.deletePlayer(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/textures", permission = BlessingSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse uploadTexture(PluginHttpRequest request) {
        return http.uploadTexture(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/closet", permission = BlessingSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse closet(PluginHttpRequest request) {
        return http.closet(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/closet", permission = BlessingSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse saveClosetItem(PluginHttpRequest request) {
        return http.saveClosetItem(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/closet/{id}", permission = BlessingSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse renameClosetItem(PluginHttpRequest request) {
        return http.renameClosetItem(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/closet/{id}", permission = BlessingSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse deleteClosetItem(PluginHttpRequest request) {
        return http.deleteClosetItem(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/settings", permission = BlessingSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse settings() {
        return http.settings();
    }

    @PluginHttpEndpoint(method = "PUT", path = "/settings", permission = BlessingSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse saveSettings(PluginHttpRequest request) {
        return http.saveSettings(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/migration/blessing-skin", permission = BlessingSkinPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse migrate(PluginHttpRequest request) {
        return http.migrate(request);
    }
}
