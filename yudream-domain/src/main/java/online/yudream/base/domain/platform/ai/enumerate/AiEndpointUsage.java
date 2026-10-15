package online.yudream.base.domain.platform.ai.enumerate;

import java.util.Locale;

/**
 * AI 端点用途：聊天（对话生成）、向量化（Embedding）与重排序（Rerank）分别是独立端点，
 * 各自拥有基址、路径、凭据与模型清单，不共用一个供应商条目的模型列表。
 */
public enum AiEndpointUsage {

    CHAT,
    EMBEDDING,
    RERANK;

    public static AiEndpointUsage from(String value) {
        if (value == null || value.isBlank()) {
            return CHAT;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "EMBEDDING", "EMBEDDINGS", "VECTOR", "VECTORS" -> EMBEDDING;
            case "RERANK", "RERANKING", "RERANKER" -> RERANK;
            default -> CHAT;
        };
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return switch (this) {
            case EMBEDDING -> "向量化";
            case RERANK -> "重排序";
            case CHAT -> "聊天";
        };
    }
}
