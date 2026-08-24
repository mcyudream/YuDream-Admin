package online.yudream.base.domain.platform.graph.valobj;

public record GraphProjectionResult(boolean success, long nodeCount, long relationshipCount,
                                    long durationMillis, String errorMessage) {

    public GraphProjectionResult {
        nodeCount = Math.max(nodeCount, 0);
        relationshipCount = Math.max(relationshipCount, 0);
        durationMillis = Math.max(durationMillis, 0);
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static GraphProjectionResult failure(long durationMillis, String errorMessage) {
        return new GraphProjectionResult(false, 0, 0, durationMillis, errorMessage);
    }
}
