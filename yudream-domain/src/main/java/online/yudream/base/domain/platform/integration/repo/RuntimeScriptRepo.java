package online.yudream.base.domain.platform.integration.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;

import java.util.Optional;

public interface RuntimeScriptRepo {

    RuntimeScript save(RuntimeScript script);

    Optional<RuntimeScript> findById(Long id);

    Optional<RuntimeScript> findByCode(String code);

    PageResult<RuntimeScript> page(String keyword, int page, int size);
}
