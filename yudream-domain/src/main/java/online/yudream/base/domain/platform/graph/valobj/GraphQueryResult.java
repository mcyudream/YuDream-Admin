package online.yudream.base.domain.platform.graph.valobj;

import online.yudream.base.domain.platform.graph.enumerate.GraphQueryStatus;

import java.util.List;
import java.util.Map;

public record GraphQueryResult(
        List<Map<String, Object>> rows,
        String summary,
        long durationMillis,
        GraphQueryStatus status,
        String errorMessage
) {
}
