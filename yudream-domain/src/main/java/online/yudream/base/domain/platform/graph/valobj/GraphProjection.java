package online.yudream.base.domain.platform.graph.valobj;

import java.util.HashSet;
import java.util.List;

public record GraphProjection(String namespace, String versionId, List<GraphProjectionNode> nodes,
                              List<GraphProjectionRelationship> relationships) {
    public GraphProjection {
        namespace = required(namespace, "图投影命名空间不能为空");
        versionId = required(versionId, "图投影版本不能为空");
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        relationships = relationships == null ? List.of() : List.copyOf(relationships);
        var nodeIds = new HashSet<String>();
        nodes.forEach(node -> {
            if (!nodeIds.add(node.id())) throw new IllegalArgumentException("图投影节点 ID 重复: " + node.id());
        });
        relationships.forEach(relationship -> {
            if (!nodeIds.contains(relationship.sourceId()) || !nodeIds.contains(relationship.targetId())) {
                throw new IllegalArgumentException("图投影关系引用了不存在的节点");
            }
        });
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
