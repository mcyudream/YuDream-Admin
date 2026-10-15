package online.yudream.base.domain.platform.ai.valobj;

/**
 * 远端返回的模型条目；{@code kind} 与端点用途一致（chat/embedding/rerank）。
 */
public record AiDiscoveredModel(String code, String name, String kind) {
}
