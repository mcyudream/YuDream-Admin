package online.yudream.base.interfaces.platform.ai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.agent.service.AgentAppService;
import online.yudream.base.application.platform.agent.service.BuiltinAgentCodes;
import online.yudream.base.application.platform.ai.service.AiAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.agent.assembler.AgentWebAssembler;
import online.yudream.base.interfaces.platform.agent.res.AgentApplicationRes;
import online.yudream.base.interfaces.platform.agent.res.AgentModelRes;
import online.yudream.base.interfaces.platform.ai.assembler.AiWebAssembler;
import online.yudream.base.interfaces.platform.ai.request.CmsPageGenerateRequest;
import online.yudream.base.interfaces.platform.ai.res.AguiStreamEventRes;
import online.yudream.base.interfaces.platform.ai.res.CmsPageGenerateRes;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/platform/ai")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiController {

    private final online.yudream.base.application.platform.ai.service.AiBusinessRunAppService businessRuns;
    private final online.yudream.base.interfaces.platform.ai.support.AiRunStreamSupport runStreams;
    private final AgentAppService agentAppService;
    private final online.yudream.base.interfaces.platform.ai.service.AguiWsTicketService aguiWsTicketService;

    @Value("${yudream.platform.ai.client.sse-timeout:30m}")
    private Duration sseTimeout;

    /**
     * CMS 构建器等 AI 场景的 Agent 列表。与 Agent 管理台的 platform:agent:view 分离：
     * 只要能用 AI 生成页面（platform:ai:generate）就能看到可切换的 Agent，否则面板会静默丢失选择器。
     */
    /**
     * 签发 AG-UI WebSocket 握手票据：浏览器 WebSocket 无法携带 Authorization 头，
     * 用一次性短时效票据替代在查询参数中暴露长效会话令牌。
     */
    @PostMapping("/ws-ticket")
    @PermissionRegister(code = "platform:ai:generate", name = "AI 生成页面", module = "平台能力", desc = "签发 AG-UI WebSocket 握手票据")
    public Result<java.util.Map<String, Object>> wsTicket() {
        String ticket = aguiWsTicketService.issue(cn.dev33.satoken.stp.StpUtil.getTokenValue());
        return Result.ok(java.util.Map.of("ticket", ticket, "expiresIn", 60));
    }

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

    @PostMapping("/cms/pages/generate")
    @PermissionRegister(code = "platform:ai:generate", name = "提交 AI 页面生成", module = "平台能力", desc = "提交后台运行并通过统一事件流读取生成进度")
    public Result<online.yudream.base.interfaces.platform.ai.res.AiRunRes> generateCmsPage(@Valid @RequestBody CmsPageGenerateRequest request) {
        return Result.ok(online.yudream.base.interfaces.platform.ai.assembler.AiRunWebAssembler.run(
                businessRuns.cms(AiWebAssembler.toCmd(request, SecurityPrincipalSupport.current()))));
    }

    @PostMapping(value = "/cms/pages/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:ai:generate", name = "AI 流式生成页面", module = "平台能力", desc = "订阅可恢复的后台页面生成运行")
    public SseEmitter streamCmsPage(@Valid @RequestBody CmsPageGenerateRequest request) {
        var principal = SecurityPrincipalSupport.current();
        var run = businessRuns.cms(AiWebAssembler.toCmd(request, principal));
        return runStreams.subscribe(principal.userId(), run.id(), 0, true);
    }
}
