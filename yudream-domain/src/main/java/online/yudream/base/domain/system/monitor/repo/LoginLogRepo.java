package online.yudream.base.domain.system.monitor.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.monitor.dto.LoginLogDTO;

public interface LoginLogRepo {

    void save(LoginLogDTO log);

    PageResult<LoginLogDTO> page(String keyword, Boolean success, int page, int size);
}
