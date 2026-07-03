package online.yudream.base.infra.platform.graph.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.graph.enumerate.GraphQueryStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformGraphQueryLog")
public class GraphQueryLogDO extends BaseDO {
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
