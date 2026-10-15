package online.yudream.base.infra.platform.ai.service.provider;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import online.yudream.base.domain.platform.ai.enumerate.AiEndpointUsage;
import online.yudream.base.domain.platform.ai.enumerate.AiProviderProtocol;

/**
 * 平台 AI 端点配置。聊天、向量化（Embedding）与重排序（Rerank）各自是独立端点：
 * 每项自带用途、协议、基址、路径、凭据与模型清单，运行期互不推导。
 */
public record AiProviderEndpoint(
        String code,
        String name,
        AiProviderType type,
        AiEndpointUsage usage,
        AiProviderProtocol protocol,
        String baseUrl,
        String completionsPath,
        String apiKey,
        String proxyUrl,
        String defaultModel,
        String temperature,
        String extraBody,
        List<AiModelEndpoint> models,
        List<String> embeddingModels,
        List<String> rerankModels,
        boolean enabled
) {
    public static final String DEFAULT_COMPLETIONS_PATH = "/chat/completions";
    public static final String DEFAULT_ANTHROPIC_MESSAGES_PATH = "/v1/messages";
    public static final String DEFAULT_RESPONSES_PATH = "/responses";
    public static final String DEFAULT_EMBEDDINGS_PATH = "/embeddings";
    public static final String DEFAULT_RERANK_PATH = "/rerank";

    public AiProviderEndpoint {
        type = type == null ? AiProviderType.OPENAI_COMPATIBLE : type;
        if (protocol == null && usage == null) {
            protocol = AiProviderProtocol.CHAT;
            usage = AiEndpointUsage.CHAT;
        }
        else if (protocol == null) {
            protocol = AiProviderProtocol.defaultFor(usage);
        }
        else if (usage == null) {
            usage = protocol.usage();
        }
        if (!protocol.supports(usage)) {
            // 用途优先：声明为向量化/重排却带了聊天协议的旧数据修正协议，不改变端点用途。
            protocol = AiProviderProtocol.defaultFor(usage);
        }
        completionsPath = normalizePath(completionsPath, defaultPath(usage, protocol));
        models = models == null ? List.of() : List.copyOf(models);
        embeddingModels = embeddingModels == null ? List.of() : List.copyOf(embeddingModels);
        rerankModels = rerankModels == null ? List.of() : List.copyOf(rerankModels);
    }

    private static String normalizePath(String path, String fallback) {
        return StringUtils.hasText(path) ? "/" + path.trim().replaceAll("^/+", "") : fallback;
    }

    private static String defaultPath(AiEndpointUsage usage, AiProviderProtocol protocol) {
        return switch (usage) {
            case EMBEDDING -> DEFAULT_EMBEDDINGS_PATH;
            case RERANK -> DEFAULT_RERANK_PATH;
            case CHAT -> switch (protocol) {
                case ANTH -> DEFAULT_ANTHROPIC_MESSAGES_PATH;
                case RESPONSE -> DEFAULT_RESPONSES_PATH;
                default -> DEFAULT_COMPLETIONS_PATH;
            };
        };
    }

    /** 端点基址（含版本段，不含端点路径）：用于交给 SDK 自行拼接路径的实现。 */
    public String endpointBaseUrl() {
        String value = StringUtils.hasText(baseUrl) ? baseUrl.trim() : "";
        String suffix = completionsPath.replaceAll("^/+", "/");
        value = value.replaceAll("/+$", "");
        if (value.endsWith(suffix)) {
            return value.substring(0, value.length() - suffix.length());
        }
        return value;
    }

    /** 端点完整地址（基址 + 路径）。 */
    public String endpointUrl() {
        return endpointBaseUrl() + completionsPath;
    }

    public String displayName() {
        return StringUtils.hasText(name) ? name.trim() : code;
    }

    public boolean isUsage(AiEndpointUsage value) {
        return usage == (value == null ? AiEndpointUsage.CHAT : value);
    }

    public boolean isChat() {
        return usage == AiEndpointUsage.CHAT;
    }

    /** 聊天模型（kind=chat），聊天端点解析默认模型时使用。 */
    public List<AiModelEndpoint> chatModels() {
        return models.stream().filter(model -> "chat".equalsIgnoreCase(model.kind())).toList();
    }

    /**
     * 该端点可用的向量化模型名：向量化端点以自身模型清单为准，并兼容旧配置显式登记
     * 在其他端点的 {@code embeddingModels}。
     */
    public List<String> embeddingModelNames() {
        return usage == AiEndpointUsage.EMBEDDING ? mergedModelNames(embeddingModels) : List.copyOf(embeddingModels);
    }

    /** 该端点可用的重排序模型名，规则同 {@link #embeddingModelNames()}。 */
    public List<String> rerankModelNames() {
        return usage == AiEndpointUsage.RERANK ? mergedModelNames(rerankModels) : List.copyOf(rerankModels);
    }

    private List<String> mergedModelNames(List<String> explicit) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String value : explicit) {
            if (StringUtils.hasText(value)) {
                names.add(value.trim());
            }
        }
        for (AiModelEndpoint model : models) {
            String name = model.modelName();
            if (StringUtils.hasText(name)) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }
}
