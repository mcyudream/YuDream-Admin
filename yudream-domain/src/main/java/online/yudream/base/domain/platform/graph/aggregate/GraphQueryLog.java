package online.yudream.base.domain.platform.graph.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.platform.graph.enumerate.GraphQueryStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class GraphQueryLog extends BaseDomain {

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
