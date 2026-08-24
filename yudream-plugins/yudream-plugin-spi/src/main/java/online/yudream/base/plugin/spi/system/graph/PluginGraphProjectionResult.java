package online.yudream.base.plugin.spi.system.graph;

/**
 * A safe result of atomically replacing one plugin-scoped resource graph projection.
 */
public record PluginGraphProjectionResult(boolean success, String namespace, long nodeCount,
                                          long relationshipCount, PluginGraphError error) {

    public PluginGraphProjectionResult {
        namespace = namespace == null ? "" : namespace.trim();
        nodeCount = Math.max(nodeCount, 0);
        relationshipCount = Math.max(relationshipCount, 0);
        if (success) {
            error = null;
        } else if (error == null) {
            error = new PluginGraphError(PluginGraphErrorCode.PROJECTION_FAILED, "Graph projection replacement failed");
        }
    }

    public static PluginGraphProjectionResult failure(PluginGraphErrorCode code, String message) {
        return new PluginGraphProjectionResult(false, "", 0, 0, new PluginGraphError(code, message));
    }
}
