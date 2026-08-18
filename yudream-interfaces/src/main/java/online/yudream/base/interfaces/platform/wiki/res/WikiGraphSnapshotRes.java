package online.yudream.base.interfaces.platform.wiki.res;

import lombok.Builder;

import java.util.List;

@Builder
public record WikiGraphSnapshotRes(
        List<Node> nodes,
        List<Edge> edges,
        List<Community> communities,
        List<Insight> insights
) {
    @Builder
    public record Node(String id, String title, String type, int degree, String community) {
    }

    @Builder
    public record Edge(String source, String target, double weight, String signal) {
    }

    @Builder
    public record Community(String id, String label, List<String> nodeIds, int size, double cohesion, boolean lowCohesion) {
    }

    @Builder
    public record Insight(String kind, String title, String description, List<String> nodeIds, List<String> searchQueries) {
    }
}
