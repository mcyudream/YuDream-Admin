package online.yudream.base.plugin.blessing.interfaces.http;

import online.yudream.base.plugin.blessing.application.service.BlessingSkinAppService;
import online.yudream.base.plugin.blessing.infrastructure.support.JsonSupport;
import online.yudream.base.plugin.blessing.interfaces.assembler.BlessingSkinWebAssembler;
import online.yudream.base.plugin.blessing.interfaces.request.AssignTextureRequest;
import online.yudream.base.plugin.blessing.interfaces.request.ClosetItemSaveRequest;
import online.yudream.base.plugin.blessing.interfaces.request.CreatePlayerRequest;
import online.yudream.base.plugin.blessing.interfaces.request.CreateSkinUserRequest;
import online.yudream.base.plugin.blessing.interfaces.request.MigrationRequest;
import online.yudream.base.plugin.blessing.interfaces.request.SkinSettingsSaveRequest;
import online.yudream.base.plugin.blessing.interfaces.request.TextureUploadRequest;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.spi.system.skin.PluginSkinProfile;
import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BlessingSkinHttpFacade {

    private final BlessingSkinAppService appService;
    private final BlessingSkinWebAssembler assembler = new BlessingSkinWebAssembler();

    public BlessingSkinHttpFacade(BlessingSkinAppService appService) {
        this.appService = appService;
    }

    public PluginHttpResponse status() {
        return PluginHttpResponse.ok(appService.summary());
    }

    public PluginHttpResponse users(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.listUsers(page(request), size(request)));
    }

    public PluginHttpResponse createUser(PluginHttpRequest request) {
        CreateSkinUserRequest body = JsonSupport.read(request.body(), CreateSkinUserRequest.class);
        return PluginHttpResponse.ok(appService.createUser(assembler.toCmd(body)));
    }

    public PluginHttpResponse players(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.listPlayers(page(request), size(request)));
    }

    public PluginHttpResponse createPlayer(PluginHttpRequest request) {
        CreatePlayerRequest body = JsonSupport.read(request.body(), CreatePlayerRequest.class);
        return PluginHttpResponse.ok(appService.createPlayer(
                assembler.toCmd(body),
                request.principal().userId()
        ));
    }

    public PluginHttpResponse player(PluginHttpRequest request) {
        String name = lastPathSegment(request.path());
        return appService.findPlayer(name)
                .map(PluginHttpResponse::ok)
                .orElseGet(() -> PluginHttpResponse.rawJson(404, Map.of("message", "角色不存在")));
    }

    public PluginHttpResponse assignTextures(PluginHttpRequest request) {
        String name = pathSegment(request.path(), 1);
        AssignTextureRequest body = JsonSupport.read(request.body(), AssignTextureRequest.class);
        return PluginHttpResponse.ok(appService.assignTextures(
                name,
                assembler.toCmd(body)
        ));
    }

    public PluginHttpResponse textures(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.listTextures(page(request), size(request)));
    }

    public PluginHttpResponse uploadTexture(PluginHttpRequest request) {
        TextureUploadRequest body = JsonSupport.read(request.body(), TextureUploadRequest.class);
        return PluginHttpResponse.ok(appService.uploadTexture(
                assembler.toCmd(body),
                request.principal().userId()
        ));
    }

    public PluginHttpResponse textureContent(PluginHttpRequest request) {
        String hash = lastPathSegment(request.path());
        return appService.readTexture(hash)
                .map(this::toBinaryResponse)
                .orElseGet(() -> PluginHttpResponse.rawJson(404, Map.of("message", "材质文件不存在")));
    }

    public PluginHttpResponse customSkinProfile(PluginHttpRequest request) {
        String name = lastPathSegment(request.path());
        PluginSkinProfile profile = appService.findProfileByName(name.replace(".json", ""))
                .orElse(null);
        if (profile == null) {
            return PluginHttpResponse.rawJson(404, Map.of("message", "角色不存在"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", profile.name());
        Map<String, String> skins = new LinkedHashMap<>();
        if (profile.skin() != null) {
            skins.put(profile.skin().model() == null ? "default" : profile.skin().model(), profile.skin().hash());
        }
        body.put("skins", skins);
        body.put("cape", profile.cape() == null ? null : profile.cape().hash());
        return PluginHttpResponse.rawJson(200, body);
    }

    public PluginHttpResponse closet(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.listCloset(firstQuery(request, "userId"), page(request), size(request)));
    }

    public PluginHttpResponse saveClosetItem(PluginHttpRequest request) {
        ClosetItemSaveRequest body = JsonSupport.read(request.body(), ClosetItemSaveRequest.class);
        return PluginHttpResponse.ok(appService.saveClosetItem(
                assembler.toCmd(body),
                request.principal().userId()
        ));
    }

    public PluginHttpResponse deleteClosetItem(PluginHttpRequest request) {
        appService.deleteClosetItem(lastPathSegment(request.path()));
        return PluginHttpResponse.ok(Map.of("deleted", true));
    }

    public PluginHttpResponse settings() {
        return PluginHttpResponse.ok(appService.settings());
    }

    public PluginHttpResponse saveSettings(PluginHttpRequest request) {
        SkinSettingsSaveRequest body = JsonSupport.read(request.body(), SkinSettingsSaveRequest.class);
        return PluginHttpResponse.ok(appService.saveSettings(assembler.toCmd(body)));
    }

    public PluginHttpResponse migrate(PluginHttpRequest request) {
        MigrationRequest body = JsonSupport.read(request.body(), MigrationRequest.class);
        return PluginHttpResponse.ok(appService.migrate(assembler.toCmd(body)));
    }

    private PluginHttpResponse toBinaryResponse(PluginStoredFile file) {
        try {
            return new PluginHttpResponse(
                    200,
                    Map.of("Cache-Control", "public, max-age=31536000"),
                    file.contentType() == null ? "image/png" : file.contentType(),
                    file.inputStream().readAllBytes(),
                    false
            );
        } catch (IOException e) {
            return PluginHttpResponse.rawJson(500, Map.of("message", "材质读取失败：" + e.getMessage()));
        }
    }

    private int page(PluginHttpRequest request) {
        return intQuery(request, "page", 1);
    }

    private int size(PluginHttpRequest request) {
        return intQuery(request, "size", 20);
    }

    private int intQuery(PluginHttpRequest request, String key, int defaultValue) {
        List<String> values = request.query().get(key);
        if (values == null || values.isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(values.get(0));
    }

    private String firstQuery(PluginHttpRequest request, String key) {
        List<String> values = request.query().get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private String lastPathSegment(String path) {
        String[] segments = trim(path).split("/");
        return decode(segments[segments.length - 1]);
    }

    private String pathSegment(String path, int indexAfterRoot) {
        String[] segments = trim(path).split("/");
        if (indexAfterRoot < 0 || indexAfterRoot >= segments.length) {
            return "";
        }
        return decode(segments[indexAfterRoot]);
    }

    private String trim(String path) {
        String value = path == null ? "" : path;
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
