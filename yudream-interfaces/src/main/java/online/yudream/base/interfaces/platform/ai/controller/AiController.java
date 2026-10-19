package online.yudream.base.interfaces.platform.ai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.agent.service.AgentAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.agent.assembler.AgentWebAssembler;
import online.yudream.base.interfaces.platform.agent.res.AgentApplicationRes;
import online.yudream.base.interfaces.platform.agent.res.AgentModelRes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 场景的模型与 Agent 目录。页面生成、画布构建等对话统一走 AI 助手网关
 * （POST /api/platform/chat/sessions/{id}/runs + /api/platform/ai/runs），这里只保留目录查询。
 */
@RestController
@RequestMapping("/api/platform/ai")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiController {

    private final AgentAppService agentAppService;

    /**
     * CMS 构建器等 AI 场景的 Agent 列表。与 Agent 管理台的 platform:agent:view 分离：
     * 只要能用 AI 生成页面（platform:ai:generate）就能看到可切换的 Agent，否则面板会静默丢失选择器。
     */
    @GetMapping("/agents/available")
    @PermissionRegister(code = "platform:ai:generate", name = "AI 生成页面", module = "平台能力", desc = "使用 AI 为 CMS 生成页面草稿")
    public Result<List<AgentApplicationRes>> availableAgents() {
        return Result.ok(agentAppService.publishedApplications().stream().map(AgentWebAssembler::toRes).toList());
    }

    /**
     * CMS 构建器等 AI 场景的可用模型清单（provider-first）。与 Agent 管理台的 platform:agent:view 分离：
     * 只要能用 AI 生成页面（platform:ai:generate）就能选择模型。
     */
    @GetMapping("/models/available")
    @PermissionRegister(code = "platform:ai:generate", name = "AI 生成页面", module = "平台能力", desc = "使用 AI 为 CMS 生成页面草稿")
    public Result<List<AgentModelRes>> availableModels() {
        return Result.ok(agentAppService.catalog().models().stream().map(AgentWebAssembler::toRes).toList());
    }
}
