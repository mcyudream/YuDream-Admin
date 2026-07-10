package online.yudream.base.domain.platform.satori.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.satori.aggregate.SatoriOperationLog;

public interface SatoriOperationLogRepo {
    SatoriOperationLog save(SatoriOperationLog log);
    PageResult<SatoriOperationLog> page(Long connectionId, int page, int size);
}
