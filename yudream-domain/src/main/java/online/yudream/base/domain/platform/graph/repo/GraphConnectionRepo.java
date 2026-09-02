package online.yudream.base.domain.platform.graph.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;

import java.util.List;
import java.util.Optional;

public interface GraphConnectionRepo {

    GraphConnection save(GraphConnection connection);

    Optional<GraphConnection> findById(Long id);

    Optional<GraphConnection> findByCode(String code);

    default Optional<GraphConnection> findActiveAuthorizedByCode(String code, String pluginCode) {
        return findByCode(code).filter(connection -> connection.active() && connection.authorizedFor(pluginCode));
    }

    default List<GraphConnection> findActiveAuthorizedByPluginCode(String pluginCode, int limit) {
        return page("", GraphConnectionStatus.ACTIVE, 1, Math.clamp(limit, 1, 100)).getRecords().stream()
                .filter(connection -> connection.authorizedFor(pluginCode)).toList();
    }

    default PageResult<GraphConnection> page(String keyword, int page, int size) {
        return page(keyword, null, page, size);
    }

    PageResult<GraphConnection> page(String keyword, GraphConnectionStatus status, int page, int size);
}
