package online.yudream.base.domain.platform.integration.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.integration.aggregate.RuntimeExecutionLog;

public interface RuntimeExecutionLogRepo {

    RuntimeExecutionLog save(RuntimeExecutionLog log);

    PageResult<RuntimeExecutionLog> page(String keyword, int page, int size);
}
