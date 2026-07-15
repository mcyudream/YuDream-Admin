package online.yudream.base.domain.platform.agent.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.agent.aggregate.AgentTool;

import java.util.Optional;

public interface AgentToolRepo {
    AgentTool save(AgentTool tool);
    Optional<AgentTool> findById(Long id);
    Optional<AgentTool> findByCode(String code);
    PageResult<AgentTool> page(String keyword, int page, int size);
    void deleteById(Long id);
}
