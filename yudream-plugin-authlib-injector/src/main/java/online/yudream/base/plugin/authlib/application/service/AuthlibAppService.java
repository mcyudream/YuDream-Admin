package online.yudream.base.plugin.authlib.application.service;

import online.yudream.base.plugin.authlib.domain.aggregate.AuthSession;
import online.yudream.base.plugin.authlib.domain.aggregate.ServerJoin;
import online.yudream.base.plugin.authlib.infrastructure.repository.AuthlibRepository;
import online.yudream.base.plugin.authlib.infrastructure.service.AuthlibCryptoService;
import online.yudream.base.plugin.authlib.infrastructure.support.JsonSupport;
import online.yudream.base.plugin.authlib.interfaces.request.AuthenticateRequest;
import online.yudream.base.plugin.authlib.interfaces.request.JoinRequest;
import online.yudream.base.plugin.authlib.interfaces.request.RefreshRequest;
import online.yudream.base.plugin.authlib.interfaces.request.SignoutRequest;
import online.yudream.base.plugin.authlib.interfaces.request.TextureBindRequest;
import online.yudream.base.plugin.authlib.interfaces.request.TokenRequest;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.system.skin.PluginSkinProfile;
import online.yudream.base.plugin.spi.system.skin.PluginSkinService;
import online.yudream.base.plugin.spi.system.skin.PluginSkinTexture;
import online.yudream.base.plugin.spi.system.skin.PluginSkinUser;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AuthlibAppService {

    private static final String SKIN_PLUGIN_CODE = "blessing-skin";
    private static final long SESSION_TTL = Duration.ofDays(7).toMillis();
    private static final long JOIN_TTL = Duration.ofMinutes(5).toMillis();

    private final PluginContext context;
    private final AuthlibRepository repository;
    private final AuthlibCryptoService cryptoService;

    public AuthlibAppService(PluginContext context, AuthlibRepository repository, AuthlibCryptoService cryptoService) {
        this.context = context;
        this.repository = repository;
        this.cryptoService = cryptoService;
    }

    public Object metadata() {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("serverName", "YuDream Authlib Injector");
        meta.put("implementationName", "YuDream Authlib Injector Plugin");
        meta.put("implementationVersion", "1.0.0");
        meta.put("links", Map.of("homepage", "https://yudream.online"));
        body.put("meta", meta);
        body.put("skinDomains", List.of(".localhost", "localhost", "127.0.0.1"));
        body.put("signaturePublickey", cryptoService.publicKeyPem());
        return body;
    }

    public Object status(String apiRoot, String textureBaseUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("apiRoot", apiRoot);
        body.put("textureBaseUrl", textureBaseUrl);
        body.put("skinPluginEnabled", context.framework().extension(SKIN_PLUGIN_CODE, PluginSkinService.class).isPresent());
        body.put("metadata", metadata());
        body.put("endpoints", List.of(
                "GET /",
                "POST /authserver/authenticate",
                "POST /authserver/refresh",
                "POST /authserver/validate",
                "POST /authserver/invalidate",
                "POST /authserver/signout",
                "POST /sessionserver/session/minecraft/join",
                "GET /sessionserver/session/minecraft/hasJoined",
                "GET /sessionserver/session/minecraft/profile/{uuid}",
                "POST /api/profiles/minecraft",
                "PUT /api/user/profile/{uuid}/{textureType}",
                "DELETE /api/user/profile/{uuid}/{textureType}"
        ));
        return body;
    }

    public Object authenticate(AuthenticateRequest request) {
        PluginSkinUser user = skinService().authenticate(request.username(), request.password())
                .orElseThrow(() -> authError("ForbiddenOperationException", "用户名或密码错误"));
        if (user.profiles().isEmpty()) {
            throw authError("ForbiddenOperationException", "当前账号没有可用角色");
        }
        String clientToken = hasText(request.clientToken()) ? request.clientToken() : UUID.randomUUID().toString();
        PluginSkinProfile selected = user.profiles().get(0);
        AuthSession session = createSession(clientToken, user, selected);
        return tokenResponse(session, user.profiles(), selected, Boolean.TRUE.equals(request.requestUser()));
    }

    public Object refresh(RefreshRequest request) {
        AuthSession oldSession = validSession(request.accessToken(), request.clientToken());
        PluginSkinProfile selected = request.selectedProfile() == null
                ? skinService().findProfileByUuid(oldSession.selectedProfileId()).orElse(null)
                : skinService().findProfileByUuid(request.selectedProfile().id()).orElse(null);
        if (selected == null) {
            throw authError("ForbiddenOperationException", "角色不存在");
        }
        List<PluginSkinProfile> profiles = List.of(selected);
        repository.deleteSession(oldSession.accessToken());
        AuthSession newSession = new AuthSession(randomToken(), oldSession.clientToken(), oldSession.userId(), oldSession.username(),
                selected.uuid(), now(), now() + SESSION_TTL);
        repository.saveSession(newSession);
        return tokenResponse(newSession, profiles, selected, Boolean.TRUE.equals(request.requestUser()));
    }

    public void validate(TokenRequest request) {
        validSession(request.accessToken(), request.clientToken());
    }

    public void invalidate(TokenRequest request) {
        repository.findSession(request.accessToken()).ifPresent(session -> repository.deleteSession(session.accessToken()));
    }

    public void signout(SignoutRequest request) {
        PluginSkinUser user = skinService().authenticate(request.username(), request.password())
                .orElseThrow(() -> authError("ForbiddenOperationException", "用户名或密码错误"));
        repository.findSessionsByUser(user.id()).forEach(session -> repository.deleteSession(session.accessToken()));
    }

    public void join(JoinRequest request) {
        AuthSession session = validSession(request.accessToken(), null);
        if (!session.selectedProfileId().equals(normalizeUuid(request.selectedProfile()))) {
            throw authError("ForbiddenOperationException", "访问令牌与角色不匹配");
        }
        PluginSkinProfile profile = skinService().findProfileByUuid(session.selectedProfileId())
                .orElseThrow(() -> authError("ForbiddenOperationException", "角色不存在"));
        repository.saveJoin(new ServerJoin(
                request.serverId() + ":" + profile.uuid(),
                request.serverId(),
                profile.uuid(),
                profile.name(),
                session.accessToken(),
                now() + JOIN_TTL
        ));
    }

    public Optional<Object> hasJoined(String username, String serverId, String textureBaseUrl, boolean unsigned) {
        return repository.findJoinsByServer(serverId).stream()
                .filter(join -> join.expiresAt() != null && join.expiresAt() > now())
                .filter(join -> join.username().equalsIgnoreCase(username))
                .findFirst()
                .flatMap(join -> skinService().findProfileByUuid(join.profileId()))
                .map(profile -> profileResponse(profile, textureBaseUrl, unsigned));
    }

    public Object profile(String uuid, String textureBaseUrl, boolean unsigned) {
        PluginSkinProfile profile = skinService().findProfileByUuid(uuid)
                .orElseThrow(() -> authError("ForbiddenOperationException", "角色不存在"));
        return profileResponse(profile, textureBaseUrl, unsigned);
    }

    public Object profiles(List<String> names) {
        return skinService().findProfilesByNames(names).stream()
                .map(this::profileSummary)
                .toList();
    }

    public void setTexture(String accessToken, String uuid, String textureType, TextureBindRequest request) {
        validSession(accessToken, null);
        skinService().setProfileTexture(uuid, textureType, request.hash());
    }

    public void clearTexture(String accessToken, String uuid, String textureType) {
        validSession(accessToken, null);
        skinService().clearProfileTexture(uuid, textureType);
    }

    public RuntimeException authError(String error, String message) {
        return new AuthlibException(error, message);
    }

    public Object errorBody(AuthlibException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", exception.error());
        body.put("errorMessage", exception.getMessage());
        return body;
    }

    private AuthSession createSession(String clientToken, PluginSkinUser user, PluginSkinProfile selected) {
        AuthSession session = new AuthSession(randomToken(), clientToken, user.id(), selected == null ? user.email() : selected.name(),
                selected == null ? null : selected.uuid(), now(), now() + SESSION_TTL);
        return repository.saveSession(session);
    }

    private Object tokenResponse(AuthSession session, List<PluginSkinProfile> profiles, PluginSkinProfile selected, boolean requestUser) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accessToken", session.accessToken());
        body.put("clientToken", session.clientToken());
        body.put("availableProfiles", profiles.stream().map(this::profileSummary).toList());
        if (selected != null) {
            body.put("selectedProfile", profileSummary(selected));
        }
        if (requestUser) {
            body.put("user", Map.of("id", session.userId(), "properties", List.of()));
        }
        return body;
    }

    private Map<String, Object> profileSummary(PluginSkinProfile profile) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", profile.uuid());
        summary.put("name", profile.name());
        return summary;
    }

    private Object profileResponse(PluginSkinProfile profile, String textureBaseUrl, boolean unsigned) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", profile.uuid());
        body.put("name", profile.name());
        body.put("properties", List.of(textureProperty(profile, textureBaseUrl, unsigned)));
        return body;
    }

    private Map<String, Object> textureProperty(PluginSkinProfile profile, String textureBaseUrl, boolean unsigned) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("name", "textures");
        String value = Base64.getEncoder().encodeToString(texturePayload(profile, textureBaseUrl).getBytes(StandardCharsets.UTF_8));
        property.put("value", value);
        if (!unsigned) {
            property.put("signature", cryptoService.sign(value));
        }
        return property;
    }

    private String texturePayload(PluginSkinProfile profile, String textureBaseUrl) {
        Map<String, Object> textures = new LinkedHashMap<>();
        if (profile.skin() != null) {
            textures.put("SKIN", textureNode(profile.skin(), textureBaseUrl));
        }
        if (profile.cape() != null) {
            textures.put("CAPE", textureNode(profile.cape(), textureBaseUrl));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", now());
        payload.put("profileId", profile.uuid());
        payload.put("profileName", profile.name());
        payload.put("textures", textures);
        return JsonSupport.write(payload);
    }

    private Map<String, Object> textureNode(PluginSkinTexture texture, String textureBaseUrl) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("url", textureBaseUrl + "/" + texture.hash());
        if ("skin".equalsIgnoreCase(texture.type()) && "slim".equalsIgnoreCase(texture.model())) {
            node.put("metadata", Map.of("model", "slim"));
        }
        return node;
    }

    private AuthSession validSession(String accessToken, String clientToken) {
        AuthSession session = repository.findSession(accessToken)
                .orElseThrow(() -> authError("ForbiddenOperationException", "访问令牌无效"));
        if (clientToken != null && !clientToken.equals(session.clientToken())) {
            throw authError("ForbiddenOperationException", "客户端令牌不匹配");
        }
        if (session.expiresAt() == null || session.expiresAt() <= now()) {
            repository.deleteSession(session.accessToken());
            throw authError("ForbiddenOperationException", "访问令牌已过期");
        }
        return session;
    }

    private PluginSkinService skinService() {
        return context.framework().extension(SKIN_PLUGIN_CODE, PluginSkinService.class)
                .orElseThrow(() -> authError("ForbiddenOperationException", "Blessing Skin 插件未启用"));
    }

    private String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeUuid(String uuid) {
        return uuid == null ? null : uuid.replace("-", "").toLowerCase(Locale.ROOT);
    }

    public static class AuthlibException extends RuntimeException {
        private final String error;

        public AuthlibException(String error, String message) {
            super(message);
            this.error = error;
        }

        public String error() {
            return error;
        }
    }
}
