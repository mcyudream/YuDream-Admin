package online.yudream.base.domain.platform.agent.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.agent.aggregate.AgentSkill;

import java.util.List;
import java.util.Optional;

public interface AgentSkillRepo {

    AgentSkill save(AgentSkill skill);

    Optional<AgentSkill> findById(Long id);

    Optional<AgentSkill> findByName(String name);

    PageResult<AgentSkill> page(String keyword, int page, int size);

    List<AgentSkill> findAllEnabled();

    void deleteById(Long id);
}
