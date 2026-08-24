package online.yudream.base.domain.platform.graph.valobj;

public record GraphProjectionViewRequest(String namespace, String versionId, int nodePage, int nodeSize,
                                         int relationshipPage, int relationshipSize) {

    public GraphProjectionViewRequest {
        namespace = required(namespace, "图投影命名空间不能为空");
        versionId = required(versionId, "图投影版本不能为空");
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
