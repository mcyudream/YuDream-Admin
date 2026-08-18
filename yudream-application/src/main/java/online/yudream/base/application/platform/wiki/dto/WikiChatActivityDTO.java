package online.yudream.base.application.platform.wiki.dto;

import lombok.Builder;

import java.util.List;

/**
 * Wiki 问答过程事件的结构化载荷，用于 AG-UI ACTIVITY_SNAPSHOT。
 * <p>
 * 所有 ID 均为字符串，避免 Java Long/Snowflake 在 JSON 边界丢失精度。hits 表示本轮预检索命中，
 * graph 表示基于命中的局部图谱（非整库快照）。
 */
@Builder
public record WikiChatActivityDTO(
        String activityType,
        String phase,
        String status,
        String title,
        String content,
        String query,
        List<Hit> hits,
        Graph graph
) {
    @Builder
    public record Hit(double score, String kind, String nodeId, String title, String path, String excerpt) {
    }

    @Builder
    public record Graph(String query, List<Node> nodes, List<Edge> edges) {
    }

    @Builder
    public record Node(String id, String title, String type, String role, double score, String path) {
    }

    @Builder
    public record Edge(String source, String target, double weight, String signal) {
    }
}
