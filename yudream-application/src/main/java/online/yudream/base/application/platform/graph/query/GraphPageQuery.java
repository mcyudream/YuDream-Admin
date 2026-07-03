package online.yudream.base.application.platform.graph.query;

import lombok.Data;

@Data
public class GraphPageQuery {
    private String keyword;
    private int page = 1;
    private int size = 10;
}
