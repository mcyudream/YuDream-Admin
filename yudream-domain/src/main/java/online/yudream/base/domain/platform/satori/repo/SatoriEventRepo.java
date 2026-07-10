package online.yudream.base.domain.platform.satori.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.satori.aggregate.SatoriEventRecord;

import java.util.Optional;

public interface SatoriEventRepo {
    SatoriEventRecord save(SatoriEventRecord event);
    Optional<SatoriEventRecord> findByIdempotencyKey(Long connectionId, String sequence);
    PageResult<SatoriEventRecord> page(Long connectionId, int page, int size);
}
