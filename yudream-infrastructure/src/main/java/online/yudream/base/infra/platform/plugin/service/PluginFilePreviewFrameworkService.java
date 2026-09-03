package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.preview.service.PluginPreviewFileOpener;
import online.yudream.base.domain.system.file.service.ObjectStorage;
import online.yudream.base.domain.system.file.valobj.StoredObject;
import online.yudream.base.infra.platform.preview.service.FilePreviewCapabilityProvider;
import online.yudream.base.plugin.spi.system.preview.PluginFilePreviewService;
import online.yudream.base.plugin.spi.system.preview.PluginPreviewFile;
import online.yudream.base.plugin.spi.system.preview.PluginPreviewInfo;
import online.yudream.base.plugin.spi.system.storage.PluginStoredFile;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 平台文件预览能力：kkFileView 配置与启停由「平台能力 &gt; 文件预览」能力模块双闸门控制
 * （部署侧 {@code PLATFORM_FILE_PREVIEW_ENABLED} 决定 {@link FilePreviewCapabilityProvider} Bean 是否存在，
 * 运行侧由管理后台启用开关决定），本类为插件文件签发 HMAC 短时效公开地址（{@link #PUBLIC_FILE_PATH} 端点回源），
 * 并按「大小上限 → 浏览器直读 → kkFileView」给出预览决策（图片/音视频/PDF/文本优先浏览器原生直读，
 * 避免 kk 跨域与模板兼容问题；Office/PSD 等格式才交给 kkFileView）。
 * 签名密钥复用宿主 {@code yudream.credential.key}（加命名空间前缀派生），不落插件侧密钥。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginFilePreviewFrameworkService implements PluginFilePreviewService, PluginPreviewFileOpener {

    /** 签名公开文件端点（PublicFilePreviewController 挂载，匿名可达，仅凭 token）。 */
    public static final String PUBLIC_FILE_PATH = "/api/public/preview/file";

    private static final Pattern PLUGIN_CODE_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final String CREDENTIAL_KEY_PROPERTY = "yudream.credential.key";
    private static final String HMAC_KEY_NAMESPACE = "plugin-file-preview:";

    private static final Set<String> IMAGE_EXTS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "avif");
    private static final Set<String> AUDIO_EXTS = Set.of(
            "mp3", "wav", "ogg", "flac", "m4a", "aac");
    private static final Set<String> BROWSER_VIDEO_EXTS = Set.of(
            "mp4", "webm", "m4v", "mov");
    private static final Set<String> TEXT_EXTS = Set.of(
            "txt", "md", "markdown", "json", "xml", "yml", "yaml", "log", "csv", "properties", "ini");

    private final ObjectProvider<FilePreviewCapabilityProvider> capabilityProvider;
    private final Environment environment;
    private final ObjectStorage objectStorage;

    @Override
    public boolean enabled() {
        return config().kkReady();
    }

    @Override
    public String kkFileViewBaseUrl() {
        return config().baseUrl();
    }

    @Override
    public String officePreviewType() {
        return config().officePreviewType();
    }

    @Override
    public long tokenTtlSeconds() {
        return config().tokenTtlSeconds();
    }

    @Override
    public long maxPreviewSizeMb() {
        return config().maxPreviewSizeMb();
    }

    @Override
    public long maxPreviewSizeBytes() {
        return config().maxPreviewSizeMb() * 1024L * 1024L;
    }

    @Override
    public String callbackBaseUrl() {
        return config().callbackBaseUrl();
    }

    @Override
    public String kkFileViewUrl(String absoluteFileUrl) {
        PreviewConfig config = config();
        if (!config.kkReady() || !StringUtils.hasText(absoluteFileUrl)) {
            return "";
        }
        String kk = trimSlash(config.baseUrl()) + "/onlinePreview?url=" + URLEncoder.encode(
                Base64.getEncoder().encodeToString(absoluteFileUrl.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
        if (!config.officePreviewType().isBlank()) {
            kk += "&officePreviewType=" + URLEncoder.encode(config.officePreviewType(), StandardCharsets.UTF_8);
        }
        return kk;
    }

    @Override
    public String signedFileUrl(String pluginCode, String objectKey, String filename) {
        String key = normalizeObjectKey(objectKey);
        if (!StringUtils.hasText(pluginCode) || !PLUGIN_CODE_PATTERN.matcher(pluginCode.trim()).matches()
                || key == null || key.contains("|")) {
            return "";
        }
        String displayName = StringUtils.hasText(filename) ? filename.trim() : lastSegment(key);
        String token;
        try {
            token = sign(pluginCode.trim(), key, config().tokenTtlSeconds());
        }
        catch (IllegalStateException e) {
            log.warn("签发插件文件预览地址失败: {}", e.getMessage());
            return "";
        }
        return PUBLIC_FILE_PATH + "/" + token + "/" + encodePath(displayName);
    }

    @Override
    public PluginPreviewInfo preview(String pluginCode, PluginPreviewFile file) {
        if (file == null || !StringUtils.hasText(file.objectKey())) {
            return PluginPreviewInfo.none("文件不存在");
        }
        PreviewConfig config = config();
        if (file.size() > config.maxPreviewSizeMb() * 1024L * 1024L) {
            return PluginPreviewInfo.none("文件超过预览大小上限（" + config.maxPreviewSizeMb() + "MB），请下载后查看");
        }
        String filename = StringUtils.hasText(file.filename()) ? file.filename() : lastSegment(file.objectKey());
        String path = signedFileUrl(pluginCode, file.objectKey(), filename);
        if (path.isEmpty()) {
            return PluginPreviewInfo.none("预览地址签发失败，请联系管理员检查平台预览配置");
        }
        if (browserRenderable(extOf(filename))) {
            return PluginPreviewInfo.direct(path);
        }
        if (config.kkReady()) {
            String base = resolveCallbackBase(config);
            if (base == null) {
                return PluginPreviewInfo.none("无法推导预览回源地址，请管理员在「平台能力 > 文件预览」中配置回源地址");
            }
            return PluginPreviewInfo.kkfile(kkFileViewUrl(base + path));
        }
        return PluginPreviewInfo.none("当前格式需要 kkFileView 才能在线预览，请下载后查看（或联系管理员在「平台能力 > 文件预览」中启用）");
    }

    @Override
    public PluginPreviewInfo previewExternal(String absoluteFileUrl, String ext, long size) {
        if (!StringUtils.hasText(absoluteFileUrl)) {
            return PluginPreviewInfo.none("文件地址为空");
        }
        PreviewConfig config = config();
        if (size > config.maxPreviewSizeMb() * 1024L * 1024L) {
            return PluginPreviewInfo.none("文件超过预览大小上限（" + config.maxPreviewSizeMb() + "MB），请下载后查看");
        }
        if (browserRenderable(ext)) {
            return PluginPreviewInfo.direct(absoluteFileUrl);
        }
        if (config.kkReady()) {
            return PluginPreviewInfo.kkfile(kkFileViewUrl(absoluteFileUrl));
        }
        return PluginPreviewInfo.none("当前格式需要 kkFileView 才能在线预览，请下载后查看（或联系管理员在「平台能力 > 文件预览」中启用）");
    }

    /**
     * 校验签名 token 并读取插件文件；token 无效/过期或对象不存在时返回空。
     * 供签名公开文件端点使用。
     */
    @Override
    public Optional<PluginStoredFile> openSignedFile(String token) {
        Optional<TokenPayload> payload = verify(token);
        if (payload.isEmpty()) {
            return Optional.empty();
        }
        String storageKey = "plugins/" + payload.get().pluginCode() + "/" + payload.get().objectKey();
        try {
            StoredObject object = objectStorage.get(storageKey);
            return Optional.of(new PluginStoredFile(
                    payload.get().objectKey(), object.contentType(), object.contentLength(), object.inputStream()));
        }
        catch (RuntimeException e) {
            log.warn("读取签名预览文件失败 {}: {}", storageKey, e.getMessage());
            return Optional.empty();
        }
    }

    // ---------- token 签发与校验 ----------

    String sign(String pluginCode, String objectKey, long ttlSeconds) {
        long expiry = Instant.now().getEpochSecond() + Math.max(ttlSeconds, 60);
        String payload = pluginCode + "|" + objectKey + "|" + expiry;
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + hmacHex(payload);
    }

    Optional<TokenPayload> verify(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(token.substring(0, dot)), StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String signature = token.substring(dot + 1);
        if (!MessageDigest.isEqual(hmacHex(payload).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        String[] parts = payload.split("\\|");
        if (parts.length != 3) {
            return Optional.empty();
        }
        String objectKey = normalizeObjectKey(parts[1]);
        if (!PLUGIN_CODE_PATTERN.matcher(parts[0]).matches() || objectKey == null) {
            return Optional.empty();
        }
        try {
            long expiry = Long.parseLong(parts[2]);
            if (expiry < Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(new TokenPayload(parts[0], objectKey, expiry));
        }
        catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String hmacHex(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        }
        catch (IllegalStateException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }

    private byte[] signingKey() {
        String credentialKey = credentialKey();
        if (!StringUtils.hasText(credentialKey)) {
            throw new IllegalStateException("平台文件预览签名密钥未配置（" + CREDENTIAL_KEY_PROPERTY + "）");
        }
        return (HMAC_KEY_NAMESPACE + credentialKey).getBytes(StandardCharsets.UTF_8);
    }

    /** 可覆写以便测试；默认读宿主凭证密钥配置。 */
    protected String credentialKey() {
        return environment.getProperty(CREDENTIAL_KEY_PROPERTY);
    }

    record TokenPayload(String pluginCode, String objectKey, long expiryEpochSeconds) {
    }

    // ---------- 配置 ----------

    private PreviewConfig config() {
        FilePreviewCapabilityProvider capability = capability();
        if (capability == null || !capability.active()) {
            return new PreviewConfig(false, "", "", DEFAULT_OFFICE_PREVIEW_TYPE,
                    DEFAULT_TOKEN_TTL_SECONDS, DEFAULT_MAX_PREVIEW_SIZE_MB);
        }
        String baseUrl = capability.configValue(CONFIG_BASE_URL).trim();
        String callbackBaseUrl = capability.configValue(CONFIG_CALLBACK_BASE_URL).trim();
        String officePreviewType = capability.configValue(CONFIG_OFFICE_PREVIEW_TYPE).trim();
        if (officePreviewType.isEmpty()) {
            officePreviewType = DEFAULT_OFFICE_PREVIEW_TYPE;
        }
        long tokenTtlSeconds = Optional.of(capability.configValue(CONFIG_TOKEN_TTL_SECONDS))
                .map(PluginFilePreviewFrameworkService::parseLong)
                .filter(value -> value >= 60 && value <= 86400)
                .orElse(DEFAULT_TOKEN_TTL_SECONDS);
        long maxPreviewSizeMb = Optional.of(capability.configValue(CONFIG_MAX_PREVIEW_SIZE_MB))
                .map(PluginFilePreviewFrameworkService::parseLong)
                .filter(value -> value >= 1 && value <= 2048)
                .orElse(DEFAULT_MAX_PREVIEW_SIZE_MB);
        return new PreviewConfig(true, baseUrl, callbackBaseUrl, officePreviewType, tokenTtlSeconds, maxPreviewSizeMb);
    }

    /** 可覆写以便测试；默认取能力 Provider Bean（部署侧闸门关闭时 Bean 不存在）。 */
    protected FilePreviewCapabilityProvider capability() {
        return capabilityProvider == null ? null : capabilityProvider.getIfAvailable();
    }

    record PreviewConfig(boolean enabled, String baseUrl, String callbackBaseUrl,
                         String officePreviewType, long tokenTtlSeconds, long maxPreviewSizeMb) {
        boolean kkReady() {
            return enabled && !baseUrl.isBlank();
        }
    }

    // ---------- 回源地址推导 ----------

    /** 优先设置中的回源基址，否则从当前请求推导（反向代理场景取 X-Forwarded-Proto + Host）。 */
    private String resolveCallbackBase(PreviewConfig config) {
        if (!config.callbackBaseUrl().isBlank()) {
            return trimSlash(config.callbackBaseUrl());
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            String host = request.getHeader("Host");
            if (StringUtils.hasText(host)) {
                String proto = request.getHeader("X-Forwarded-Proto");
                if (!StringUtils.hasText(proto)) {
                    proto = request.getScheme();
                }
                return proto + "://" + host.trim();
            }
        }
        return null;
    }

    // ---------- 工具 ----------

    /** 未启用 kkFileView 时浏览器无需插件即可直接渲染的格式。 */
    static boolean browserRenderable(String ext) {
        if (!StringUtils.hasText(ext)) {
            return false;
        }
        String normalized = ext.trim().toLowerCase(Locale.ROOT);
        return IMAGE_EXTS.contains(normalized)
                || AUDIO_EXTS.contains(normalized)
                || BROWSER_VIDEO_EXTS.contains(normalized)
                || "pdf".equals(normalized)
                || TEXT_EXTS.contains(normalized);
    }

    static String extOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 对象键规范化：与 ObjectStoragePluginFileStore 同一套约束，非法返回 null。 */
    private static String normalizeObjectKey(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        String normalized = objectKey.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!StringUtils.hasText(normalized) || normalized.endsWith("/")) {
            return null;
        }
        for (String segment : normalized.split("/")) {
            if (!StringUtils.hasText(segment) || ".".equals(segment) || "..".equals(segment)) {
                return null;
            }
        }
        return normalized;
    }

    private static String lastSegment(String objectKey) {
        int slash = objectKey.lastIndexOf('/');
        return slash < 0 ? objectKey : objectKey.substring(slash + 1);
    }

    private static String trimSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** 路径段编码：URLEncoder 会把空格编成 +，路径中需换成 %20。 */
    private static String encodePath(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        }
        catch (RuntimeException e) {
            return -1;
        }
    }
}
