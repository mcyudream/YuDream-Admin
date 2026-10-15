package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.formatter.Formatter;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.model.transport.HttpTransportConfig;
import io.agentscope.core.model.transport.OkHttpTransport;
import io.agentscope.core.model.transport.ProxyConfig;
import io.agentscope.extensions.model.anthropic.AnthropicChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.compat.deepseek.DeepSeekFormatter;
import io.agentscope.extensions.model.openai.compat.kimi.KimiFormatter;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import io.agentscope.extensions.model.openaiofficial.OpenAIResponsesChatModel;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.infra.platform.ai.service.AiClientProperties;
import online.yudream.base.infra.platform.ai.service.provider.AiModelEndpoint;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderEndpoint;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderType;
import online.yudream.base.infra.platform.ai.service.provider.ResolvedAiModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AgentScope {@link Model} 工厂：按端点协议选择模型实现，并按「协议 + 端点 + 模型」身份做 LRU 缓存复用连接。
 * <ul>
 *   <li>{@code CHAT}：OpenAI Chat Completions；Kimi/DeepSeek 等兼容供应商的协议细节交给框架 compat 适配。</li>
 *   <li>{@code ANTH}：Anthropic Messages，路径与 x-api-key 鉴权由 anthropic SDK 负责。</li>
 *   <li>{@code RESPONSE}：OpenAI Responses，/responses 路径由官方 SDK 负责。</li>
 * </ul>
 */
@Slf4j
@Component
public class AgentScopeModels {

    private static final int MAX_CACHED_MODELS = 16;
    private static final String DEFAULT_ANTHROPIC_BASE_URL = "https://api.anthropic.com";

    private final AiClientProperties clientProperties;
    private final Map<String, Model> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Model> eldest) {
            return size() > MAX_CACHED_MODELS;
        }
    };

    public AgentScopeModels(AiClientProperties clientProperties) {
        this.clientProperties = clientProperties;
    }

    /**
     * 按配置身份解析（带缓存的）AgentScope 模型实例；配置任意字段变化都会得到新实例。
     */
    public Model resolve(ResolvedAiModel resolved) {
        String key = identityOf(resolved.provider(), resolved.model());
        synchronized (cache) {
            return cache.computeIfAbsent(key, ignored -> build(resolved.provider(), resolved.model()));
        }
    }

    /** 模型连接的缓存身份；协议不同即不同实例，Agent 在每次执行时绑定所选模型。 */
    public static String identityOf(AiProviderEndpoint provider, AiModelEndpoint model) {
        return String.join("|",
                String.valueOf(provider.type()),
                String.valueOf(provider.usage()),
                String.valueOf(provider.protocol()),
                provider.endpointBaseUrl(),
                provider.completionsPath(),
                String.valueOf(provider.apiKey()),
                String.valueOf(proxyUrlNormalized(provider.proxyUrl())),
                model.modelName());
    }

    private Model build(AiProviderEndpoint provider, AiModelEndpoint model) {
        return switch (provider.protocol()) {
            case ANTH -> anthropicChatModel(provider, model);
            case RESPONSE -> responsesChatModel(provider, model);
            default -> openAiCompatibleChatModel(provider, model);
        };
    }

    private Model openAiCompatibleChatModel(AiProviderEndpoint provider, AiModelEndpoint model) {
        AiProviderType type = provider.type();
        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(provider.apiKey())
                .baseUrl(provider.endpointBaseUrl())
                .endpointPath(provider.completionsPath())
                .modelName(model.modelName())
                .stream(true)
                .formatter(formatterFor(type))
                .httpTransport(transport(provider));
        if (type == AiProviderType.KIMI || type == AiProviderType.DEEPSEEK) {
            // Kimi/DeepSeek 兼容 API 会优先遵循 response_format 而跳过工具调用，
            // 结构化输出与工具调用共存时必须走框架的合成工具降级路径。
            builder.nativeStructuredOutputWithTools(false);
        }
        return builder.build();
    }

    private Model anthropicChatModel(AiProviderEndpoint provider, AiModelEndpoint model) {
        AnthropicChatModel.Builder builder = AnthropicChatModel.builder()
                .apiKey(provider.apiKey())
                .baseUrl(anthropicBaseUrl(provider))
                .modelName(model.modelName())
                .stream(true);
        ProxyConfig proxy = proxyConfig(provider.proxyUrl());
        if (proxy != null) {
            builder.proxy(proxy);
        }
        return builder.build();
    }

    private Model responsesChatModel(AiProviderEndpoint provider, AiModelEndpoint model) {
        OpenAIResponsesChatModel.Builder builder = OpenAIResponsesChatModel.builder()
                .apiKey(provider.apiKey())
                .baseUrl(provider.endpointBaseUrl())
                .modelName(model.modelName())
                .stream(true);
        ProxyConfig proxy = proxyConfig(provider.proxyUrl());
        if (proxy != null) {
            builder.proxy(proxy);
        }
        return builder.build();
    }

    /** Anthropic SDK 自行拼接版本路径，基址缺省时回落官方地址。 */
    private String anthropicBaseUrl(AiProviderEndpoint provider) {
        String baseUrl = provider.endpointBaseUrl();
        return StringUtils.hasText(baseUrl) ? baseUrl : DEFAULT_ANTHROPIC_BASE_URL;
    }

    private HttpTransport transport(AiProviderEndpoint provider) {
        HttpTransportConfig.Builder config = HttpTransportConfig.builder()
                .connectTimeout(clientProperties.getConnectTimeout())
                .responseTimeout(clientProperties.getReadTimeout())
                .streamIdleTimeout(clientProperties.getSseTimeout());
        ProxyConfig proxy = proxyConfig(provider.proxyUrl());
        if (proxy != null) {
            config.proxy(proxy);
        }
        return OkHttpTransport.builder().config(config.build()).build();
    }

    static Formatter<io.agentscope.extensions.model.openai.dto.OpenAIMessage,
            io.agentscope.extensions.model.openai.dto.OpenAIResponse,
            io.agentscope.extensions.model.openai.dto.OpenAIRequest> formatterFor(AiProviderType type) {
        return switch (type) {
            case KIMI -> new KimiFormatter();
            case DEEPSEEK -> new DeepSeekFormatter();
            case OPENAI, OPENAI_COMPATIBLE -> new OpenAIChatFormatter();
        };
    }

    /**
     * 代理地址解析；与旧网关行为一致：省略协议时按 HTTP 处理，非法配置直接抛业务异常。
     */
    static ProxyConfig proxyConfig(String proxyUrl) {
        if (!StringUtils.hasText(proxyUrl)) {
            return null;
        }
        String value = proxyUrl.trim();
        if (!value.contains("://")) {
            value = "http://" + value;
        }
        URI uri = URI.create(value);
        if (!StringUtils.hasText(uri.getHost()) || uri.getPort() <= 0) {
            throw new IllegalArgumentException("AI 代理地址配置无效：代理地址必须包含主机和端口");
        }
        return ProxyConfig.http(uri.getHost(), uri.getPort());
    }

    private static String proxyUrlNormalized(String proxyUrl) {
        return StringUtils.hasText(proxyUrl) ? proxyUrl.trim().toLowerCase() : "";
    }
}
