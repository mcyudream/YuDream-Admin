package online.yudream.base.domain.platform.ai.enumerate;

import java.util.Locale;

/**
 * AI 端点线协议（wire protocol）。聊天端点支持三种协议：
 * {@code ANTH} = Anthropic Messages、{@code CHAT} = OpenAI Chat Completions、
 * {@code RESPONSE} = OpenAI Responses；{@code EMBEDDING}/{@code RERANK} 是向量化与重排序端点的固定协议。
 * <p>
 * 协议决定运行时的模型实现、默认路径与鉴权方式，不只是展示标签；保存与迁移不得静默改变既有端点的请求语义。
 */
public enum AiProviderProtocol {

    ANTH,
    CHAT,
    RESPONSE,
    EMBEDDING,
    RERANK;

    public static AiProviderProtocol from(String value) {
        if (value == null || value.isBlank()) {
            return CHAT;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ANTH", "ANTHROPIC", "ANTHROPIC_MESSAGES", "MESSAGES", "CLAUDE" -> ANTH;
            case "RESPONSE", "RESPONSES", "OPENAI_RESPONSES", "OPENAI_RESPONSE" -> RESPONSE;
            case "EMBEDDING", "EMBEDDINGS", "VECTOR", "VECTORS" -> EMBEDDING;
            case "RERANK", "RERANKING", "RERANKER" -> RERANK;
            default -> CHAT;
        };
    }

    /** 未显式声明协议时按用途给出默认协议。 */
    public static AiProviderProtocol defaultFor(AiEndpointUsage usage) {
        return switch (usage == null ? AiEndpointUsage.CHAT : usage) {
            case EMBEDDING -> EMBEDDING;
            case RERANK -> RERANK;
            case CHAT -> CHAT;
        };
    }

    /** 协议对应的端点用途：EMBEDDING/RERANK 协议固定服务同名用途，其余都是聊天端点。 */
    public AiEndpointUsage usage() {
        return switch (this) {
            case EMBEDDING -> AiEndpointUsage.EMBEDDING;
            case RERANK -> AiEndpointUsage.RERANK;
            default -> AiEndpointUsage.CHAT;
        };
    }

    public boolean supports(AiEndpointUsage usage) {
        return usage() == (usage == null ? AiEndpointUsage.CHAT : usage);
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return switch (this) {
            case ANTH -> "Anthropic Messages";
            case CHAT -> "OpenAI Chat Completions";
            case RESPONSE -> "OpenAI Responses";
            case EMBEDDING -> "Embedding";
            case RERANK -> "Rerank";
        };
    }
}
