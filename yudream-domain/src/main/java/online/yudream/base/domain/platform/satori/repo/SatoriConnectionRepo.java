package online.yudream.base.domain.platform.satori.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;

import java.util.Optional;
import java.util.List;

public interface SatoriConnectionRepo {
    SatoriConnection save(SatoriConnection connection);
    Optional<SatoriConnection> findById(Long id);
    List<SatoriConnection> findEnabled();
    PageResult<SatoriConnection> page(String keyword, int page, int size);
}
