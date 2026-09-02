package online.yudream.base.application.platform.graph.query;

import lombok.Data;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;

@Data
public class GraphPageQuery {
    private String keyword;
    private int page = 1;
    private int size = 10;
    private GraphConnectionStatus status;
}
