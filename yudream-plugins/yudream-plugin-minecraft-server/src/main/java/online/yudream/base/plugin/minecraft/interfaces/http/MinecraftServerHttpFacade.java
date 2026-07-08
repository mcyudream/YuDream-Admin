package online.yudream.base.plugin.minecraft.interfaces.http;

import online.yudream.base.plugin.minecraft.application.service.MinecraftServerAppService;
import online.yudream.base.plugin.minecraft.infrastructure.support.JsonSupport;
import online.yudream.base.plugin.minecraft.interfaces.assembler.MinecraftServerWebAssembler;
import online.yudream.base.plugin.minecraft.interfaces.request.MinecraftSeasonOpenRequest;
import online.yudream.base.plugin.minecraft.interfaces.request.MinecraftServerSaveRequest;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MinecraftServerHttpFacade {

    private final MinecraftServerAppService appService;
    private final MinecraftServerWebAssembler assembler = new MinecraftServerWebAssembler();

    public MinecraftServerHttpFacade(MinecraftServerAppService appService) {
        this.appService = appService;
    }

    public PluginHttpResponse list(PluginHttpRequest request) {
        boolean includeDisabled = boolQuery(request, "includeDisabled", false);
        boolean refresh = boolQuery(request, "refresh", false);
        return PluginHttpResponse.ok(appService.listServers(includeDisabled, refresh).stream().map(assembler::toRes).toList());
    }

    public PluginHttpResponse detail(PluginHttpRequest request) {
        return PluginHttpResponse.ok(assembler.toRes(appService.detail(pathSegment(request.path(), 1), boolQuery(request, "refresh", false))));
    }

    public PluginHttpResponse save(PluginHttpRequest request) {
        MinecraftServerSaveRequest body = JsonSupport.read(request.body(), MinecraftServerSaveRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.saveServer(assembler.toCmd(body))));
    }

    public PluginHttpResponse delete(PluginHttpRequest request) {
        appService.deleteServer(pathSegment(request.path(), 1));
        return PluginHttpResponse.ok(Map.of("deleted", true));
    }

    public PluginHttpResponse refreshStatus(PluginHttpRequest request) {
        return PluginHttpResponse.ok(assembler.toRes(appService.detail(pathSegment(request.path(), 1), true)));
    }

    public PluginHttpResponse previewOpenSeason(PluginHttpRequest request) {
        MinecraftSeasonOpenRequest body = JsonSupport.read(request.body(), MinecraftSeasonOpenRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.previewOpenSeason(pathSegment(request.path(), 1), assembler.toCmd(body), userId(request))));
    }

    public PluginHttpResponse openSeason(PluginHttpRequest request) {
        MinecraftSeasonOpenRequest body = JsonSupport.read(request.body(), MinecraftSeasonOpenRequest.class);
        return PluginHttpResponse.ok(assembler.toRes(appService.openSeason(pathSegment(request.path(), 1), assembler.toCmd(body), userId(request))));
    }

    public PluginHttpResponse rollbackSeason(PluginHttpRequest request) {
        return PluginHttpResponse.ok(assembler.toRes(appService.rollbackSeasonOperation(pathSegment(request.path(), 1), userId(request))));
    }

    public PluginHttpResponse operations(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.operations(pathSegment(request.path(), 1)).stream().map(assembler::toRes).toList());
    }

    public PluginHttpResponse myRecords(PluginHttpRequest request) {
        return PluginHttpResponse.ok(appService.userRecords(pathSegment(request.path(), 1), userId(request)).stream().map(assembler::toRes).toList());
    }

    private String userId(PluginHttpRequest request) {
        Long userId = request.principal().userId();
        if (userId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return String.valueOf(userId);
    }

    private boolean boolQuery(PluginHttpRequest request, String key, boolean defaultValue) {
        java.util.List<String> values = request.query().get(key);
        return values == null || values.isEmpty() ? defaultValue : Boolean.parseBoolean(values.get(0));
    }

    private String pathSegment(String path, int index) {
        String[] segments = trim(path).split("/");
        return index >= 0 && index < segments.length ? decode(segments[index]) : null;
    }

    private String trim(String path) {
        String value = path == null ? "" : path.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private String decode(String value) {
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
