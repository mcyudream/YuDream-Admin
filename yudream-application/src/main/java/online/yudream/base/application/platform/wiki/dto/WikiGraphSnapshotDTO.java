package online.yudream.base.application.platform.wiki.dto;

import java.util.List;

public record WikiGraphSnapshotDTO(
        List<Node> nodes,
        List<Edge> edges,
        List<Community> communities,
        List<Insight> insights
) {
    public record Node(String id, String title, String type, int degree, String community) {
    }

    public record Edge(String source, String target, double weight, String signal) {
    }

    public record Community(String id, String label, List<String> nodeIds, int size, double cohesion, boolean lowCohesion) {
    }

    public record Insight(String kind, String title, String description, List<String> nodeIds, List<String> searchQueries) {
    }
}
