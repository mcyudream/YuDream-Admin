package online.yudream.base.domain.platform.integration.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.integration.aggregate.HttpInvocationLog;

public interface HttpInvocationLogRepo {

    HttpInvocationLog save(HttpInvocationLog log);

    PageResult<HttpInvocationLog> page(String keyword, int page, int size);
}
