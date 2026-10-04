package online.yudream.base.application.platform.agent.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.cmd.AgentSkillSaveCmd;
import online.yudream.base.application.platform.agent.query.AgentSkillPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentSkill;
import online.yudream.base.domain.platform.agent.repo.AgentSkillRepo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 平台技能库（框架级统一管理）：所有 Harness Agent 的技能来源，
 * 启用的技能随每轮推理进入 &lt;available_skills&gt; 目录。
 */
@Service
@RequiredArgsConstructor
public class AgentSkillAppService {

    private final AgentSkillRepo skillRepo;

    public PageResult<AgentSkill> page(AgentSkillPageQuery query) {
        return skillRepo.page(query.getKeyword(), query.getPage(), query.getSize());
    }

    public AgentSkill detail(Long id) {
        return skillRepo.findById(id).orElseThrow(() -> new BizException("技能不存在"));
    }

    public AgentSkill save(AgentSkillSaveCmd cmd) {
        AgentSkill skill = cmd.getId() == null
                ? AgentSkill.create(cmd.getName(), cmd.getDescription(), cmd.getContent(), cmd.getResources())
                : requireSkill(cmd.getId());
        skill.apply(cmd.getName(), cmd.getDescription(), cmd.getContent(), cmd.getResources(),
                cmd.getEnabled(), skill.getBuiltin());
        return skillRepo.save(skill);
    }

    public AgentSkill setEnabled(Long id, boolean enabled) {
        AgentSkill skill = requireSkill(id);
        skill.apply(skill.getName(), skill.getDescription(), skill.getContent(), skill.getResources(),
                enabled, skill.getBuiltin());
        return skillRepo.save(skill);
    }

    public void delete(Long id) {
        AgentSkill skill = requireSkill(id);
        if (Boolean.TRUE.equals(skill.getBuiltin())) {
            throw new BizException("内置技能不可删除，可停用后隐藏");
        }
        skillRepo.deleteById(id);
    }

    public List<AgentSkill> allEnabled() {
        return skillRepo.findAllEnabled();
    }

    private AgentSkill requireSkill(Long id) {
        if (id == null) {
            throw new BizException("技能不存在");
        }
        if (!StringUtils.hasText(id.toString())) {
            throw new BizException("技能不存在");
        }
        return skillRepo.findById(id).orElseThrow(() -> new BizException("技能不存在"));
    }
}
