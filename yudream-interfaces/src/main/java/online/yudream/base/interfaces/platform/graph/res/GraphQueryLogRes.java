package online.yudream.base.interfaces.platform.graph.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.graph.enumerate.GraphQueryStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class GraphQueryLogRes {
    private Long id;
    private Long connectionId;
    private String connectionCode;
    private String cypher;
    private Map<String, Object> params;
    private List<Map<String, Object>> rows;
    private String summary;
    private long durationMillis;
    private GraphQueryStatus status;
    private String errorMessage;
    private LocalDateTime executedAt;
}
