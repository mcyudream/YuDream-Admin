package online.yudream.base.plugin.spi.system.graph;

import java.util.List;

/**
 * A complete, host-bound graph snapshot. Every retained node and relationship must be submitted
 * together; plugins cannot select a table, impersonate a plugin, or submit Cypher.
 */
public record PluginGraphCompleteProjection(String namespace, String versionId,
                                            List<PluginGraphProjectionNode> nodes,
                                            List<PluginGraphProjectionRelationship> relationships) {
    public PluginGraphCompleteProjection {
        PluginGraphProjection projection = new PluginGraphProjection(namespace, versionId, nodes, relationships);
        namespace = projection.namespace();
        versionId = projection.versionId();
        nodes = projection.nodes();
        relationships = projection.relationships();
    }
}
