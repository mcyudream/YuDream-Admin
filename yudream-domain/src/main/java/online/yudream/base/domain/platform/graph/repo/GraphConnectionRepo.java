package online.yudream.base.domain.platform.graph.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;

import java.util.Optional;

public interface GraphConnectionRepo {

    GraphConnection save(GraphConnection connection);

    Optional<GraphConnection> findById(Long id);

    Optional<GraphConnection> findByCode(String code);

    PageResult<GraphConnection> page(String keyword, int page, int size);
}
