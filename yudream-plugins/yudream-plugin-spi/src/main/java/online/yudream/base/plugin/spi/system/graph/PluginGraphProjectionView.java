package online.yudream.base.plugin.spi.system.graph;

/**
 * Structured, read-only view of one versioned resource graph projection.
 * Nodes and relationships are independently paged by the host's fixed schema queries.
 */
public record PluginGraphProjectionView(boolean success, String namespace, String versionId,
                                        PluginGraphProjectionPage<PluginGraphProjectionNode> nodes,
                                        PluginGraphProjectionPage<PluginGraphProjectionRelationship> relationships,
                                        PluginGraphError error) {

    public PluginGraphProjectionView {
        namespace = namespace == null ? "" : namespace.trim();
        versionId = versionId == null ? "" : versionId.trim();
        nodes = nodes == null ? PluginGraphProjectionPage.empty(1, 1) : nodes;
        relationships = relationships == null ? PluginGraphProjectionPage.empty(1, 1) : relationships;
        if (success) {
            error = null;
        } else if (error == null) {
            error = new PluginGraphError(PluginGraphErrorCode.PROJECTION_FAILED, "Graph projection read failed");
        }
    }

    public static PluginGraphProjectionView failure(PluginGraphErrorCode code, String message,
                                                    PluginGraphProjectionViewRequest request) {
        return failure(code, message, request == null ? null : new PluginGraphProjectionReadRequest(
                request.namespace(), request.versionId(), request.nodePage(), request.nodeSize(),
                request.relationshipPage(), request.relationshipSize()));
    }

    public static PluginGraphProjectionView failure(PluginGraphErrorCode code, String message,
                                                    PluginGraphProjectionReadRequest request) {
        int nodePage = request == null ? 1 : request.nodePage();
        int nodeSize = request == null ? 1 : request.nodeSize();
        int relationshipPage = request == null ? 1 : request.relationshipPage();
        int relationshipSize = request == null ? 1 : request.relationshipSize();
        return new PluginGraphProjectionView(false, "", "", PluginGraphProjectionPage.empty(nodePage, nodeSize),
                PluginGraphProjectionPage.empty(relationshipPage, relationshipSize), new PluginGraphError(code, message));
    }
}
