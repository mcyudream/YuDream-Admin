package online.yudream.base.domain.platform.satori.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;

import java.util.Optional;

public interface SatoriConnectionRepo {
    SatoriConnection save(SatoriConnection connection);
    Optional<SatoriConnection> findById(Long id);
    PageResult<SatoriConnection> page(String keyword, int page, int size);
}
