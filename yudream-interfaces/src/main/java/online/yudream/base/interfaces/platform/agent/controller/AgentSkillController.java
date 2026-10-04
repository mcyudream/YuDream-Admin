package online.yudream.base.interfaces.platform.agent.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.cmd.AgentSkillSaveCmd;
import online.yudream.base.application.platform.agent.query.AgentSkillPageQuery;
import online.yudream.base.application.platform.agent.service.AgentSkillAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.agent.aggregate.AgentSkill;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台技能库管理（框架级统一技能源）：技能以 SKILL.md 形式进入所有 Harness Agent 的
 * &lt;available_skills&gt; 目录，由模型按需加载。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/platform/agent-skills")
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.agent", name = "enabled", havingValue = "true")
public class AgentSkillController {

    private final AgentSkillAppService skillAppService;

    @GetMapping
    @PermissionRegister(code = "platform:agent-skill:view", name = "查看 Agent 技能", module = "平台能力", desc = "查看平台技能库列表")
    public Result<PageResult<AgentSkill>> page(AgentSkillPageQuery query) {
        return Result.ok(skillAppService.page(query));
    }

    @GetMapping("/enabled")
    @PermissionRegister(code = "platform:agent-skill:view", name = "查看启用技能", module = "平台能力", desc = "查看全部启用的技能")
    public Result<List<AgentSkill>> enabled() {
        return Result.ok(skillAppService.allEnabled());
    }

    @GetMapping("/{id}")
    @PermissionRegister(code = "platform:agent-skill:view", name = "查看技能详情", module = "平台能力", desc = "查看技能内容与资源")
    public Result<AgentSkill> detail(@PathVariable Long id) {
        return Result.ok(skillAppService.detail(id));
    }

    @PostMapping
    @PermissionRegister(code = "platform:agent-skill:create", name = "创建 Agent 技能", module = "平台能力", desc = "新增平台技能")
    public Result<AgentSkill> save(@RequestBody AgentSkillSaveCmd cmd) {
        return Result.ok(skillAppService.save(cmd));
    }

    @PutMapping("/{id}/enabled/{enabled}")
    @PermissionRegister(code = "platform:agent-skill:edit", name = "启停 Agent 技能", module = "平台能力", desc = "启用或停用平台技能")
    public Result<AgentSkill> setEnabled(@PathVariable Long id, @PathVariable boolean enabled) {
        return Result.ok(skillAppService.setEnabled(id, enabled));
    }

    @DeleteMapping("/{id}")
    @PermissionRegister(code = "platform:agent-skill:delete", name = "删除 Agent 技能", module = "平台能力", desc = "删除平台技能")
    public Result<Void> delete(@PathVariable Long id) {
        skillAppService.delete(id);
        return Result.ok(null);
    }
}
