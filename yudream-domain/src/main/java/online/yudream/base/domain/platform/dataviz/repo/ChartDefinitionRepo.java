package online.yudream.base.domain.platform.dataviz.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.dataviz.aggregate.ChartDefinition;

import java.util.Optional;

public interface ChartDefinitionRepo {

    PageResult<ChartDefinition> page(String keyword, int page, int size);

    Optional<ChartDefinition> findById(Long id);

    Optional<ChartDefinition> findByCode(String code);

    ChartDefinition save(ChartDefinition chartDefinition);
}
