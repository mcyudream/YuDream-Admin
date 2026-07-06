package online.yudream.base.plugin.authlib.interfaces.http;

import online.yudream.base.plugin.authlib.application.service.AuthlibAppService;
import online.yudream.base.plugin.authlib.application.service.AuthlibAppService.AuthlibException;
import online.yudream.base.plugin.authlib.infrastructure.support.JsonSupport;
import online.yudream.base.plugin.authlib.interfaces.request.AuthenticateRequest;
import online.yudream.base.plugin.authlib.interfaces.request.JoinRequest;
import online.yudream.base.plugin.authlib.interfaces.request.RefreshRequest;
import online.yudream.base.plugin.authlib.interfaces.request.SignoutRequest;
import online.yudream.base.plugin.authlib.interfaces.request.TextureBindRequest;
import online.yudream.base.plugin.authlib.interfaces.request.TokenRequest;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AuthlibHttpFacade {

    private final AuthlibAppService appService;

    public AuthlibHttpFacade(AuthlibAppService appService) {
        this.appService = appService;
    }

    public PluginHttpResponse metadata(PluginHttpRequest request) {
        return ali(request, PluginHttpResponse.rawJson(200, appService.metadata()));
    }

    public PluginHttpResponse status(PluginHttpRequest request) {
        return ali(request, PluginHttpResponse.ok(appService.status(apiRoot(request), textureBaseUrl(request))));
    }

    public PluginHttpResponse authenticate(PluginHttpRequest request) {
        return runJson(request, () -> appService.authenticate(JsonSupport.read(request.body(), AuthenticateRequest.class)));
    }

    public PluginHttpResponse refresh(PluginHttpRequest request) {
        return runJson(request, () -> appService.refresh(JsonSupport.read(request.body(), RefreshRequest.class)));
    }

    public PluginHttpResponse validate(PluginHttpRequest request) {
        return runNoContent(request, () -> appService.validate(JsonSupport.read(request.body(), TokenRequest.class)));
    }

    public PluginHttpResponse invalidate(PluginHttpRequest request) {
        return runNoContent(request, () -> appService.invalidate(JsonSupport.read(request.body(), TokenRequest.class)));
    }

    public PluginHttpResponse signout(PluginHttpRequest request) {
        return runNoContent(request, () -> appService.signout(JsonSupport.read(request.body(), SignoutRequest.class)));
    }

    public PluginHttpResponse join(PluginHttpRequest request) {
        return runNoContent(request, () -> appService.join(JsonSupport.read(request.body(), JoinRequest.class)));
    }

    public PluginHttpResponse hasJoined(PluginHttpRequest request) {
        try {
            String username = firstQuery(request, "username");
            String serverId = firstQuery(request, "serverId");
            boolean unsigned = unsigned(request);
            return appService.hasJoined(username, serverId, textureBaseUrl(request), unsigned)
                    .map(body -> ali(request, PluginHttpResponse.rawJson(200, body)))
                    .orElseGet(() -> ali(request, PluginHttpResponse.noContent()));
        } catch (AuthlibException e) {
            return error(request, 403, e);
        }
    }

    public PluginHttpResponse profile(PluginHttpRequest request) {
        return runJson(request, () -> appService.profile(lastPathSegment(request.path()), textureBaseUrl(request), unsigned(request)));
    }

    public PluginHttpResponse profiles(PluginHttpRequest request) {
        return runJson(request, () -> appService.profiles(JsonSupport.readStringList(request.body())));
    }

    public PluginHttpResponse setTexture(PluginHttpRequest request) {
        return runNoContent(request, () -> appService.setTexture(
                bearerToken(request),
                pathSegment(request.path(), 3),
                pathSegment(request.path(), 4),
                JsonSupport.read(request.body(), TextureBindRequest.class)
        ));
    }

    public PluginHttpResponse clearTexture(PluginHttpRequest request) {
        return runNoContent(request, () -> appService.clearTexture(
                bearerToken(request),
                pathSegment(request.path(), 3),
                pathSegment(request.path(), 4)
        ));
    }

    private PluginHttpResponse runJson(PluginHttpRequest request, JsonAction action) {
        try {
            return ali(request, PluginHttpResponse.rawJson(200, action.run()));
        } catch (AuthlibException e) {
            return error(request, 403, e);
        } catch (RuntimeException e) {
            return error(request, 400, new AuthlibException("IllegalArgumentException", e.getMessage()));
        }
    }

    private PluginHttpResponse runNoContent(PluginHttpRequest request, VoidAction action) {
        try {
            action.run();
            return ali(request, PluginHttpResponse.noContent());
        } catch (AuthlibException e) {
            return error(request, 403, e);
        } catch (RuntimeException e) {
            return error(request, 400, new AuthlibException("IllegalArgumentException", e.getMessage()));
        }
    }

    private PluginHttpResponse error(PluginHttpRequest request, int status, AuthlibException exception) {
        return ali(request, PluginHttpResponse.rawJson(status, appService.errorBody(exception)));
    }

    private PluginHttpResponse ali(PluginHttpRequest request, PluginHttpResponse response) {
        Map<String, String> headers = new LinkedHashMap<>(response.headers());
        headers.put("X-Authlib-Injector-API-Location", apiRoot(request));
        return new PluginHttpResponse(response.status(), headers, response.contentType(), response.body(), response.wrapped());
    }

    private String apiRoot(PluginHttpRequest request) {
        return origin(request) + "/api/plugins/authlib-injector";
    }

    private String textureBaseUrl(PluginHttpRequest request) {
        return origin(request) + "/api/plugins/yudream-skin/textures";
    }

    private String origin(PluginHttpRequest request) {
        String proto = header(request, "x-forwarded-proto");
        if (proto == null) {
            proto = "http";
        }
        String host = header(request, "x-forwarded-host");
        if (host == null) {
            host = header(request, "host");
        }
        return proto + "://" + (host == null ? "localhost:8080" : host);
    }

    private String header(PluginHttpRequest request, String name) {
        for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    private String bearerToken(PluginHttpRequest request) {
        String authorization = header(request, "authorization");
        if (authorization == null || !authorization.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            throw new AuthlibException("ForbiddenOperationException", "缺少 Bearer 访问令牌");
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private boolean unsigned(PluginHttpRequest request) {
        return !"false".equalsIgnoreCase(firstQuery(request, "unsigned"));
    }

    private String firstQuery(PluginHttpRequest request, String key) {
        List<String> values = request.query().get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private String lastPathSegment(String path) {
        String[] segments = trim(path).split("/");
        return decode(segments[segments.length - 1]);
    }

    private String pathSegment(String path, int index) {
        String[] segments = trim(path).split("/");
        return index >= 0 && index < segments.length ? decode(segments[index]) : null;
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

    private interface JsonAction {
        Object run();
    }

    private interface VoidAction {
        void run();
    }
}
