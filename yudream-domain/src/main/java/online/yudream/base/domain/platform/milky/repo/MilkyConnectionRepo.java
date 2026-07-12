package online.yudream.base.domain.platform.milky.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import java.util.List;
import java.util.Optional;

public interface MilkyConnectionRepo {
    MilkyConnection save(MilkyConnection connection);
    Optional<MilkyConnection> findById(Long id);
    List<MilkyConnection> findEnabled();
    PageResult<MilkyConnection> page(String keyword, int page, int size);
}
