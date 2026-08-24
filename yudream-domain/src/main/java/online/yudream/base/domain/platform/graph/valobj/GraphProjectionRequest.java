package online.yudream.base.domain.platform.graph.valobj;

import java.util.Map;

public record GraphProjectionRequest(String projectionName, String nodeQuery, String relationshipQuery,
                                     Map<String, Object> configuration) {

    public GraphProjectionRequest {
        projectionName = projectionName == null ? "" : projectionName;
        nodeQuery = nodeQuery == null ? "" : nodeQuery;
        relationshipQuery = relationshipQuery == null ? "" : relationshipQuery;
        configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
    }
}
