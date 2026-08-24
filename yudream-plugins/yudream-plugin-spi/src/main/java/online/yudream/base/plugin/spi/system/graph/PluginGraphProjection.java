package online.yudream.base.plugin.spi.system.graph;

import java.util.HashSet;
import java.util.List;

public record PluginGraphProjection(String namespace, String versionId, List<PluginGraphProjectionNode> nodes,
                                    List<PluginGraphProjectionRelationship> relationships) {
    public PluginGraphProjection {
        namespace = required(namespace, "Graph projection namespace is required");
        versionId = required(versionId, "Graph projection version is required");
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        relationships = relationships == null ? List.of() : List.copyOf(relationships);
        var nodeIds = new HashSet<String>();
        nodes.forEach(node -> {
            if (!nodeIds.add(node.id())) throw new IllegalArgumentException("Duplicate graph projection node id: " + node.id());
        });
        var relationshipIds = new HashSet<String>();
        relationships.forEach(relationship -> {
            if (!relationshipIds.add(relationship.id())) {
                throw new IllegalArgumentException("Duplicate graph projection relationship id: " + relationship.id());
            }
            if (!nodeIds.contains(relationship.sourceId()) || !nodeIds.contains(relationship.targetId())) {
                throw new IllegalArgumentException("Graph projection relationship references an unknown node");
            }
        });
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
