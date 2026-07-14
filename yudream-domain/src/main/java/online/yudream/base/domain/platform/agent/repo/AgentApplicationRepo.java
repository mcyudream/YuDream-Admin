package online.yudream.base.domain.platform.agent.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;

import java.util.Optional;
import java.util.List;

public interface AgentApplicationRepo {
    AgentApplication save(AgentApplication application);
    Optional<AgentApplication> findById(Long id);
    Optional<AgentApplication> findByCode(String code);
    PageResult<AgentApplication> page(String keyword, AgentApplicationStatus status, int page, int size);
    List<AgentApplication> findByToolCode(String toolCode);
    void deleteById(Long id);
}
