package online.yudream.base.domain.platform.graph.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.graph.aggregate.GraphQueryLog;

public interface GraphQueryLogRepo {

    GraphQueryLog save(GraphQueryLog log);

    PageResult<GraphQueryLog> page(String keyword, int page, int size);
}
