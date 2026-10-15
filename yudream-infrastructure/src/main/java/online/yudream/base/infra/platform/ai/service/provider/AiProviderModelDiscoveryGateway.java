package online.yudream.base.infra.platform.ai.service.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.enumerate.AiEndpointUsage;
import online.yudream.base.domain.platform.ai.enumerate.AiProviderProtocol;
import online.yudream.base.domain.platform.ai.service.AiModelDiscoveryGateway;
import online.yudream.base.domain.platform.ai.valobj.AiDiscoveredModel;
import online.yudream.base.domain.platform.ai.valobj.AiModelDiscoveryQuery;
import online.yudream.base.domain.platform.ai.valobj.AiModelDiscoveryResult;
import online.yudream.base.application.common.net.OutboundNetworkPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * 端点模型发现：按协议调用远端模型列表接口（OpenAI 兼容 {@code /models}、Anthropic {@code /v1/models}），
 * 结果按模型编码去重排序。凭据只随本次请求发送，不落库、不写日志。
 * <p>
 * 出站目标校验只做输入合法性（http/https、主机存在、不携带用户凭据）：
 * 同一权限层已可通过聊天端点向相同地址发起生成请求，发现接口不应比实际调用更严格；
 * 需要限制内网目标时由部署侧统一通过 {@code yudream.security.outbound.allow-private-network} 收紧。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiProviderModelDiscoveryGateway implements AiModelDiscoveryGateway {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final AiProviderConfigParser providerConfigParser;
    private final OutboundNetworkPolicy outboundNetworkPolicy;

    public AiProviderModelDiscoveryGateway(
            AiProviderConfigParser providerConfigParser,
            OutboundNetworkPolicy outboundNetworkPolicy
    ) {
        this.providerConfigParser = providerConfigParser;
        this.outboundNetworkPolicy = outboundNetworkPolicy;
    }

    @Override
    public AiModelDiscoveryResult discover(AiModelDiscoveryQuery query) {
        AiEndpointUsage usage = AiEndpointUsage.from(query.usage());
        AiProviderProtocol protocol = AiProviderProtocol.from(query.protocol());
        if (!protocol.supports(usage)) {
            protocol = AiProviderProtocol.defaultFor(usage);
        }
        AiProviderEndpoint endpoint = resolveEndpoint(query, usage, protocol);
        if (!StringUtils.hasText(endpoint.baseUrl())) {
            throw new BizException("请先填写 " + usage.displayName() + " 端点地址");
        }
        if (!StringUtils.hasText(endpoint.apiKey())) {
            throw new BizException("请先填写 " + usage.displayName() + " 端点凭据");
        }
        if (usage == AiEndpointUsage.RERANK) {
            // 重排序服务普遍没有模型列表接口，直接提示手工登记，避免给出误导性的失败。
            return AiModelDiscoveryResult.unsupported("重排序服务通常不提供模型列表接口，请手工登记模型编码");
        }
        String url = modelListUrl(endpoint, usage, protocol);
        outboundNetworkPolicy.validate(url, "AI 模型列表", true);
        List<AiDiscoveredModel> models = requestModels(url, endpoint, protocol, usage);
        if (models.isEmpty()) {
            return AiModelDiscoveryResult.unsupported("远端未返回任何模型，请确认该地址提供模型列表接口");
        }
        return AiModelDiscoveryResult.listed(models, "已获取 " + models.size() + " 个模型");
    }

    /**
     * 合并管理端输入与已保存端点：留空字段回落到保存值，因此「获取模型」无需重新粘贴已保存的密钥。
     */
    private AiProviderEndpoint resolveEndpoint(AiModelDiscoveryQuery query, AiEndpointUsage usage, AiProviderProtocol protocol) {
        Optional<AiProviderEndpoint> stored = providerConfigParser
                .find(query.storedConfig(), usage, query.providerCode());
        return new AiProviderEndpoint(
                firstText(query.providerCode(), stored.map(AiProviderEndpoint::code).orElse("discover")),
                stored.map(AiProviderEndpoint::displayName).orElse(""),
                stored.map(AiProviderEndpoint::type).orElse(AiProviderType.OPENAI_COMPATIBLE),
                usage,
                protocol,
                firstText(query.baseUrl(), stored.map(AiProviderEndpoint::baseUrl).orElse("")),
                "",
                firstText(query.apiKey(), stored.map(AiProviderEndpoint::apiKey).orElse("")),
                firstText(query.proxyUrl(), stored.map(AiProviderEndpoint::proxyUrl).orElse("")),
                "",
                "",
                "",
                List.of(),
                List.of(),
                List.of(),
                true
        );
    }

    private String modelListUrl(AiProviderEndpoint endpoint, AiEndpointUsage usage, AiProviderProtocol protocol) {
        String base = stripTrailingSlash(endpoint.endpointBaseUrl());
        if (protocol == AiProviderProtocol.ANTH) {
            return StringUtils.hasText(base) ? base + "/v1/models" : "https://api.anthropic.com/v1/models";
        }
        if (usage == AiEndpointUsage.EMBEDDING && !StringUtils.hasText(endpoint.endpointBaseUrl())) {
            return "https://api.openai.com/v1/models";
        }
        return base + "/models";
    }

    private List<AiDiscoveredModel> requestModels(
            String url,
            AiProviderEndpoint endpoint,
            AiProviderProtocol protocol,
            AiEndpointUsage usage
    ) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(READ_TIMEOUT)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .GET();
        if (protocol == AiProviderProtocol.ANTH) {
            request.header("x-api-key", endpoint.apiKey());
            request.header("anthropic-version", ANTHROPIC_VERSION);
        }
        else {
            request.header("Authorization", "Bearer " + endpoint.apiKey());
        }
        try {
            HttpClient.Builder client = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NEVER);
            applyProxy(endpoint.proxyUrl(), client);
            HttpResponse<String> response = client.build()
                    .send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new BizException("模型列表请求失败（HTTP " + response.statusCode() + "）：" + summary(response.body()));
            }
            return parseModels(response.body(), usage);
        }
        catch (BizException exception) {
            throw exception;
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BizException("模型列表请求被中断");
        }
        catch (Exception exception) {
            throw new BizException("模型列表请求失败：" + describe(exception));
        }
    }

    private List<AiDiscoveredModel> parseModels(String body, AiEndpointUsage usage) {
        if (!StringUtils.hasText(body)) {
            return List.of();
        }
        try {
            JSONObject json = JSONUtil.parseObj(body);
            Object raw = json.get("data");
            if (!(raw instanceof JSONArray array)) {
                raw = json.get("models");
            }
            if (!(raw instanceof JSONArray array)) {
                return List.of();
            }
            LinkedHashSet<AiDiscoveredModel> models = new LinkedHashSet<>();
            for (Object item : array) {
                if (item instanceof CharSequence text) {
                    String code = text.toString().trim();
                    if (StringUtils.hasText(code)) {
                        models.add(new AiDiscoveredModel(code, code, usage.code()));
                    }
                    continue;
                }
                if (!(item instanceof JSONObject model)) {
                    continue;
                }
                String code = firstText(model.getStr("id"), model.getStr("model"), model.getStr("name"));
                if (!StringUtils.hasText(code)) {
                    continue;
                }
                String name = firstText(model.getStr("display_name"), model.getStr("name"), code);
                models.add(new AiDiscoveredModel(code, name, usage.code()));
            }
            List<AiDiscoveredModel> result = new ArrayList<>(models);
            result.sort(Comparator.comparing(AiDiscoveredModel::code, String.CASE_INSENSITIVE_ORDER));
            return result;
        }
        catch (Exception exception) {
            throw new BizException("模型列表响应格式无法解析：" + describe(exception));
        }
    }

    private void applyProxy(String proxyUrl, HttpClient.Builder client) {
        if (!StringUtils.hasText(proxyUrl)) {
            return;
        }
        String value = proxyUrl.trim();
        if (!value.contains("://")) {
            value = "http://" + value;
        }
        URI uri = URI.create(value);
        if (!StringUtils.hasText(uri.getHost()) || uri.getPort() <= 0) {
            throw new BizException("AI 代理地址配置无效：代理地址必须包含主机和端口");
        }
        client.proxy(ProxySelector.of(new InetSocketAddress(uri.getHost(), uri.getPort())));
    }

    private String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String summary(String body) {
        if (!StringUtils.hasText(body)) {
            return "无响应内容";
        }
        String text = body.trim().replaceAll("\\s+", " ");
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }

    private String describe(Exception exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
