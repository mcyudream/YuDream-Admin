package online.yudream.base.plugin.spi.system.graph;

/** Read input for a versioned plugin projection bound by the host to the calling plugin. */
public record PluginGraphProjectionReadRequest(String namespace, String versionId, int nodePage, int nodeSize,
                                               int relationshipPage, int relationshipSize) {
    public PluginGraphProjectionReadRequest {
        namespace = namespace == null ? "" : namespace.trim();
        versionId = versionId == null ? "" : versionId.trim();
    }
}
