package online.yudream.base.infra.platform.preview.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import online.yudream.base.plugin.spi.system.preview.PluginFilePreviewService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文件预览能力 Provider：双闸门控制 —— 部署侧由环境变量
 * {@code PLATFORM_FILE_PREVIEW_ENABLED}（{@code yudream.platform.capabilities.file-preview.enabled}）
 * 决定本 Bean 是否存在，运行侧由管理后台「平台能力 &gt; 文件预览」的启用开关与配置（入库）决定运行时是否生效。
 * 统一持有 kkFileView 配置，启用状态供 {@code PluginFilePreviewFrameworkService}（SPI 实现）经
 * {@link ObjectProvider} 读取；连通性探测在 {@link #test(String)} 中执行。
 */
@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.file-preview", name = "enabled", havingValue = "true")
public class FilePreviewCapabilityProvider implements CapabilityProvider {

    public static final String CODE = PluginFilePreviewService.CAPABILITY_CODE;
    /** kkFileView 容器内网地址（连通性测试兜底用，仅环境变量，不入库）：compose 内为 http://kkfileview:8012。 */
    public static final String INTERNAL_URL_PROPERTY = "file.preview.kkfileview.internal-url";

    private final Environment environment;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private volatile Map<String, String> config = Map.of();

    public FilePreviewCapabilityProvider(Environment environment) {
        this.environment = environment;
    }

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "文件预览",
                CapabilityType.DOCUMENT,
                "集成 kkFileView 为插件文件提供 Office、PSD、压缩包等格式的在线预览；未启用时仅浏览器可直读格式可预览",
                "i-ri:file-eye-line",
                45,
                Map.of(
                        PluginFilePreviewService.CONFIG_BASE_URL, "http://localhost/kkfileview",
                        PluginFilePreviewService.CONFIG_CALLBACK_BASE_URL, "",
                        PluginFilePreviewService.CONFIG_OFFICE_PREVIEW_TYPE, PluginFilePreviewService.DEFAULT_OFFICE_PREVIEW_TYPE,
                        PluginFilePreviewService.CONFIG_TOKEN_TTL_SECONDS, String.valueOf(PluginFilePreviewService.DEFAULT_TOKEN_TTL_SECONDS),
                        PluginFilePreviewService.CONFIG_MAX_PREVIEW_SIZE_MB, String.valueOf(PluginFilePreviewService.DEFAULT_MAX_PREVIEW_SIZE_MB)
                )
        );
    }

    @Override
    public CapabilityHealth health() {
        if (!enabled.get()) {
            return CapabilityHealth.disabled("文件预览能力未启用");
        }
        return CapabilityHealth.enabled("文件预览能力已启用",
                Map.of("baseUrl", configValue(PluginFilePreviewService.CONFIG_BASE_URL)));
    }

    @Override
    public void enable(Map<String, String> config) {
        Map<String, String> merged = mergedConfig(config);
        String baseUrl = merged.get(PluginFilePreviewService.CONFIG_BASE_URL);
        if (!StringUtils.hasText(baseUrl)) {
            throw new BizException("启用文件预览能力前请先配置 kkFileView 服务地址（baseUrl）");
        }
        if (!baseUrl.matches("^https?://.+")) {
            throw new BizException("kkFileView 服务地址必须以 http:// 或 https:// 开头");
        }
        String callbackBaseUrl = merged.get(PluginFilePreviewService.CONFIG_CALLBACK_BASE_URL);
        if (StringUtils.hasText(callbackBaseUrl) && !callbackBaseUrl.matches("^https?://.+")) {
            throw new BizException("回源地址必须以 http:// 或 https:// 开头");
        }
        this.config = merged;
        enabled.set(true);
    }

    @Override
    public void disable() {
        enabled.set(false);
    }

    /** kkFileView 连通性测试：请求服务首页，任何 2xx~4xx HTTP 响应都视为可达。 */
    @Override
    public CapabilityTestResult test(String message) {
        if (!enabled.get()) {
            return CapabilityTestResult.failure("文件预览能力未启用");
        }
        String target = configValue(PluginFilePreviewService.CONFIG_BASE_URL).trim();
        if (target.isEmpty()) {
            return CapabilityTestResult.failure("请先配置 kkFileView 服务地址");
        }
        ProbeResult primary = probe(target);
        if (primary.success()) {
            return CapabilityTestResult.success(primary.message());
        }
        // 同源反代部署时浏览器地址（http(s)://站点/kkfileview）从后端容器未必可达（如本机 localhost 部署），
        // 配置了容器内网地址（file.preview.kkfileview.internal-url，如 http://kkfileview:8012）则用它兜底探测，避免误报
        String internal = environment == null ? null : environment.getProperty(INTERNAL_URL_PROPERTY);
        if (StringUtils.hasText(internal)) {
            ProbeResult fallback = probe(internal.trim());
            if (fallback.success()) {
                return CapabilityTestResult.success("内网地址可达（" + fallback.message() + "）；浏览器地址经 nginx 反代提供，后端直连失败可忽略");
            }
        }
        return CapabilityTestResult.failure(primary.message());
    }

    /** 运行侧闸门：管理后台启用后（且部署侧 Bean 存在）为 true。 */
    public boolean active() {
        return enabled.get();
    }

    /** 当前生效配置值；未配置返回空串。 */
    public String configValue(String key) {
        return config.getOrDefault(key, "");
    }

    private Map<String, String> mergedConfig(Map<String, String> config) {
        Map<String, String> merged = new LinkedHashMap<>(descriptor().defaultConfig());
        if (config != null) {
            config.forEach((key, value) -> merged.put(key, value == null ? "" : value.trim()));
        }
        return merged;
    }

    private static ProbeResult probe(String target) {
        long started = System.currentTimeMillis();
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            long latency = System.currentTimeMillis() - started;
            if (response.statusCode() >= 200 && response.statusCode() < 500) {
                return new ProbeResult(true, "连接成功（HTTP " + response.statusCode() + "，" + latency + "ms）", latency);
            }
            return new ProbeResult(false, "服务返回 HTTP " + response.statusCode(), latency);
        }
        catch (Exception e) {
            return new ProbeResult(false, "连接失败：" + e.getMessage(), System.currentTimeMillis() - started);
        }
    }

    private record ProbeResult(boolean success, String message, long latencyMs) {
    }
}
