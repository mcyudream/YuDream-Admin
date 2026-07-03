package online.yudream.base.domain.platform.integration.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.integration.aggregate.HttpConnector;

import java.util.Optional;

public interface HttpConnectorRepo {

    HttpConnector save(HttpConnector connector);

    Optional<HttpConnector> findById(Long id);

    Optional<HttpConnector> findByCode(String code);

    PageResult<HttpConnector> page(String keyword, int page, int size);
}
