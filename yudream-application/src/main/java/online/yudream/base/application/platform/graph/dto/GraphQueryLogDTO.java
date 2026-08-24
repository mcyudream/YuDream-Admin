package online.yudream.base.application.platform.graph.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.graph.enumerate.GraphQueryStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class GraphQueryLogDTO {
    private Long id;
    private Long tableId;
    private String tableCode;
    private String cypher;
    private Map<String, Object> params;
    private List<Map<String, Object>> rows;
    private String summary;
    private long durationMillis;
    private GraphQueryStatus status;
    private String errorMessage;
    private LocalDateTime executedAt;
}
