package online.yudream.base.domain.system.monitor.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.monitor.dto.ApiLogDTO;

public interface ApiLogRepo {

    void save(ApiLogDTO log);

    PageResult<ApiLogDTO> page(String keyword, Boolean success, int page, int size);
}
