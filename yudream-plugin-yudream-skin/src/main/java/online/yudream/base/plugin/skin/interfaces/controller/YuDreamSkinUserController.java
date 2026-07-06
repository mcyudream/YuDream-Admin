package online.yudream.base.plugin.skin.interfaces.controller;

import online.yudream.base.plugin.skin.bootstrap.YuDreamSkinPlugin;
import online.yudream.base.plugin.skin.interfaces.http.YuDreamSkinHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

public class YuDreamSkinUserController {

    private final YuDreamSkinHttpFacade http;

    public YuDreamSkinUserController(YuDreamSkinHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/me", permission = YuDreamSkinPlugin.USER_PERMISSION)
    public PluginHttpResponse me(PluginHttpRequest request) {
        return http.me(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/me/players", permission = YuDreamSkinPlugin.USER_PERMISSION)
    public PluginHttpResponse myPlayers(PluginHttpRequest request) {
        return http.myPlayers(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/me/players", permission = YuDreamSkinPlugin.USER_PERMISSION)
    public PluginHttpResponse createMyPlayer(PluginHttpRequest request) {
        return http.createMyPlayer(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/me/players/{name}/name", permission = YuDreamSkinPlugin.USER_PERMISSION)
    public PluginHttpResponse renameMyPlayer(PluginHttpRequest request) {
        return http.renameMyPlayer(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/me/players/{name}/textures", permission = YuDreamSkinPlugin.USER_PERMISSION)
    public PluginHttpResponse assignMyTextures(PluginHttpRequest request) {
        return http.assignMyTextures(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/me/default-player", permission = YuDreamSkinPlugin.USER_PERMISSION)
    public PluginHttpResponse saveMyDefaultPlayer(PluginHttpRequest request) {
        return http.saveMyDefaultPlayer(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/me/players/{name}", permission = YuDreamSkinPlugin.USER_PERMISSION)
    public PluginHttpResponse deleteMyPlayer(PluginHttpRequest request) {
        return http.deleteMyPlayer(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/me/textures", permission = YuDreamSkinPlugin.USER_PERMISSION)
    public PluginHttpResponse uploadMyTexture(PluginHttpRequest request) {
        return http.uploadMyTexture(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/me/closet", permission = YuDreamSkinPlugin.USER_PERMISSION)
    public PluginHttpResponse myCloset(PluginHttpRequest request) {
        return http.myCloset(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/me/closet", permission = YuDreamSkinPlugin.USER_PERMISSION)
    public PluginHttpResponse saveMyClosetItem(PluginHttpRequest request) {
        return http.saveMyClosetItem(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/me/closet/{id}", permission = YuDreamSkinPlugin.USER_PERMISSION)
    public PluginHttpResponse renameMyClosetItem(PluginHttpRequest request) {
        return http.renameMyClosetItem(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/me/closet/{id}", permission = YuDreamSkinPlugin.USER_PERMISSION)
    public PluginHttpResponse deleteMyClosetItem(PluginHttpRequest request) {
        return http.deleteMyClosetItem(request);
    }
}
