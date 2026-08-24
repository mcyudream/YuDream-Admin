package online.yudream.base.domain.platform.graph.valobj;

public record GraphProjectionReplaceResult(String namespace, long nodeCount, long relationshipCount) {
    public GraphProjectionReplaceResult {
        namespace = namespace == null ? "" : namespace.trim();
        nodeCount = Math.max(nodeCount, 0);
        relationshipCount = Math.max(relationshipCount, 0);
    }
}
