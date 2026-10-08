package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.service.AgentPermissionGateway;
import online.yudream.base.domain.platform.agent.service.AgentToolExecutionGuard;
import online.yudream.base.domain.platform.agent.valobj.AgentToolPermissions;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.HarnessAgentGateway;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import io.agentscope.core.event.ConfirmResult;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunResult;
import online.yudream.base.domain.platform.ai.valobj.PendingToolConfirmation;
import online.yudream.base.domain.platform.ai.valobj.ToolConfirmationDecision;
import online.yudream.base.infra.platform.ai.service.AiClientProperties;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderAdapter;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderConfigParser;
import online.yudream.base.infra.platform.ai.service.provider.ResolvedAiModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Harness 级 Agent 运行时：按 agentCode 装配单例 {@link HarnessAgent}（工作区/记忆/压缩经
 * MongoDB 分布式存储多副本共享），运行时以 RuntimeContext 注入会话身份、权限快照、按次
 * 模型选择与系统提示词覆盖。对话上下文由框架 AgentState 管理，平台消息表仅作 UI 投影。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(HarnessRuntimeProperties.class)
public class HarnessAgentRuntime implements HarnessAgentGateway {

    private final AgentScopeModels models;
    private final AiProviderConfigParser providerConfigParser;
    private final List<AiProviderAdapter> providerAdapters;
    private final AiClientProperties aiClientProperties;
    private final HarnessRuntimeProperties properties;
    private final MongoAgentStateStore stateStore;
    private final MongoBaseStore baseStore;
    private final AgentPermissionGateway permissionGateway;
    private final ObjectProvider<AiAgentTool> aiAgentToolProvider;
    private final MongoAgentSkillRepository skillRepository;
    private AgentToolExecutionGuard toolExecutionGuard = AgentToolExecutionGuard.ALLOW_ALL;
    private final Map<String, HarnessAgent> agents = new ConcurrentHashMap<>();
    private volatile AgentToolCatalog toolkitCatalog = AgentToolCatalog.disabled();

    @Autowired(required = false)
    void setToolExecutionGuard(AgentToolExecutionGuard toolExecutionGuard) {
        this.toolExecutionGuard = toolExecutionGuard == null ? AgentToolExecutionGuard.ALLOW_ALL : toolExecutionGuard;
    }

    public HarnessAgentRuntime(
            AgentScopeModels models,
            AiProviderConfigParser providerConfigParser,
            List<AiProviderAdapter> providerAdapters,
            AiClientProperties aiClientProperties,
            HarnessRuntimeProperties properties,
            MongoAgentStateStore stateStore,
            MongoBaseStore baseStore,
            AgentPermissionGateway permissionGateway,
            ObjectProvider<AiAgentTool> aiAgentToolProvider,
            MongoAgentSkillRepository skillRepository
    ) {
        this.models = models;
        this.providerConfigParser = providerConfigParser;
        this.providerAdapters = providerAdapters;
        this.aiClientProperties = aiClientProperties;
        this.properties = properties;
        this.stateStore = stateStore;
        this.baseStore = baseStore;
        this.permissionGateway = permissionGateway;
        this.aiAgentToolProvider = aiAgentToolProvider;
        this.skillRepository = skillRepository;
    }

    @Override
    public HarnessRunResult run(
            HarnessRunRequest request,
            Consumer<String> onDelta,
            Consumer<String> onReasoningDelta,
            Consumer<AiAgentToolResult> onTool
    ) {
        // 模型在构建期绑定（agent 按 (profile, 模型身份) 缓存），不依赖 middleware 替换。
        AgentModelSelection selection = selection(request);
        String cacheKey = agentCode(request) + "::"
                + (selection == null ? "default" : String.valueOf(selection.modelKey()));
        HarnessAgent agent = agents.computeIfAbsent(cacheKey,
                ignored -> buildAgent(agentCode(request), selection));
        HarnessToolCollector collector = new HarnessToolCollector(onTool);
        String sessionId = request.sessionId() == null ? "once-" + UUID.randomUUID() : request.sessionId();
        RuntimeContext context = RuntimeContext.builder()
                .userId(userId(request))
                .sessionId(sessionId)
                .put(AgentToolPermissions.class, AgentToolPermissions.explicit(request.permissionCodes()))
                .put(AgentToolCatalog.class, catalog(request))
                .put(AgentSystemPromptOverride.class, new AgentSystemPromptOverride(request.systemPrompt()))
                .put(HarnessToolCollector.class, collector)
                .build();

        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<PendingToolConfirmation> pending = new ArrayList<>();
        List<Msg> inputs = request.confirmDecisions().isEmpty()
                ? inputMessages(request)
                : resumeMessages(agent, agentCode(request), userId(request), sessionId, request.confirmDecisions());
        AiUsage usage = consume(agent.streamEvents(inputs, context), content, reasoning, pending);
        return new HarnessRunResult(content.toString(), reasoning.toString(), usage,
                collector.snapshot(), pending);
    }

    /**
     * HITL 恢复：把用户的批准/拒绝决定包装为携带 METADATA_CONFIRM_RESULTS 的消息
     * 交回 agent 续跑（框架按 toolCallId 匹配暂挂的 ASK 工具调用，approved=false
     * 时以权限拒绝反馈给模型继续推理）。
     */
    private List<Msg> resumeMessages(HarnessAgent agent, String agentCode, String userId, String sessionId,
                                     List<ToolConfirmationDecision> decisions) {
        Map<String, Boolean> decisionById = new java.util.LinkedHashMap<>();
        for (ToolConfirmationDecision decision : decisions) {
            if (StringUtils.hasText(decision.toolCallId())) {
                decisionById.put(decision.toolCallId(), decision.approved());
            }
        }
        // 从持久化会话状态找回暂挂的 ASK 工具调用块（跨 HTTP 请求恢复）
        io.agentscope.core.state.AgentState state = stateStore
                .get(agentCode, userId, sessionId, io.agentscope.core.state.AgentState.class)
                .orElse(null);
        List<ConfirmResult> confirmations = new ArrayList<>();
        if (state != null) {
            for (Msg msg : state.getContext()) {
                for (io.agentscope.core.message.ContentBlock block : msg.getContent()) {
                    if (block instanceof ToolUseBlock toolCall
                            && toolCall.getState() == io.agentscope.core.message.ToolCallState.ASKING
                            && toolCall.getId() != null
                            && decisionById.containsKey(toolCall.getId())) {
                        confirmations.add(new ConfirmResult(decisionById.get(toolCall.getId()), toolCall));
                    }
                }
            }
        }
        if (confirmations.isEmpty()) {
            throw new online.yudream.base.domain.common.exception.BizException("没有待确认的工具调用");
        }
        Msg resume = Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent("")
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, List.copyOf(confirmations)))
                .build();
        return List.of(resume);
    }

    /**
     * 工作区文件系统：USER 隔离；工作区 profile 额外路由 cms/** 到 KV，
     * 供 CMS 静态站点文件树（pages/assets）与 IO 端点共享。
     */
    private RemoteFilesystemSpec workspaceSpec(String agentCode) {
        RemoteFilesystemSpec spec = new RemoteFilesystemSpec(baseStore)
                .isolationScope(IsolationScope.USER)
                .anonymousUserId(properties.getAnonymousUserId());
        if (workspaceProfile(agentCode)) {
            spec.addSharedPrefix("cms/");
        }
        return spec;
    }

    private String agentCode(HarnessRunRequest request) {
        return StringUtils.hasText(request.agentCode()) ? request.agentCode() : "general";
    }

    HarnessAgent agentFor(HarnessRunRequest request) {
        AgentModelSelection selection = selection(request);
        String cacheKey = agentCode(request) + "::"
                + (selection == null ? "default" : String.valueOf(selection.modelKey()));
        return agents.computeIfAbsent(cacheKey, ignored -> buildAgent(agentCode(request), selection));
    }

    /** 拥有文件工作区的 agent profile 前缀：工作流与 CMS 静态站点 builder。 */
    static boolean workspaceProfile(String agentCode) {
        return agentCode != null && (agentCode.startsWith("workflow") || agentCode.startsWith("cms-builder"));
    }

    /**
     * 模型在构建期绑定（Agent 实例按 (profile, 模型身份) 缓存多实例）；
     * 同 profile 不同供应商模型的请求各得一个实例。
     */
    HarnessAgent buildAgent(String code, AgentModelSelection selection) {
        Model model = selection == null || selection.model() == null ? defaultModel() : selection.model();
        log.info("[agent-build] code={} modelClass={} modelName={}", code,
                model.getClass().getSimpleName(), model.getModelName());
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(code)
                .agentId(code)
                .sysPrompt(defaultSystemPrompt())
                .model(model)
                .maxIters(properties.getMaxIters())
                .stateStore(stateStore)
                .distributedStore(new MongoDistributedStore(stateStore, baseStore))
                .workspace(workspaceDir())
                .filesystem(workspaceSpec(code))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(properties.getCompactionTriggerMessages())
                        .keepMessages(properties.getCompactionKeepMessages())
                        .build())
                // 数据控制：超大工具结果卸载到工作区，上下文只留首尾预览 + read_file 指针。
                .toolResultEviction(io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig.defaults())
                .maxContextTokens(properties.getMaxContextTokens())
                .permissionContext(PermissionContextState.builder()
                        .mode(PermissionMode.DEFAULT)
                        .build());
        // 聊天型 Agent 不暴露宿主文件系统与 shell；工作流/CMS builder 工作区 profile
        // 启用文件工具（read/write/edit/list/grep/glob），shell 任何 profile 都不开放。
        if (!workspaceProfile(code)) {
            builder.disableFilesystemTools();
        }
        builder.disableShellTool();
        builder.middleware(new PerCallSystemPromptMiddleware());
        builder.middleware(new PerCallToolFilterMiddleware(permissionGateway));
        if (!properties.isMemoryHooksEnabled()) {
            builder.disableMemoryHooks();
        }
        // 平台技能库（MongoDB）作为框架级统一技能源；workspace/<user>/skills 仍可按用户覆盖。
        builder.skillRepository(skillRepository);
        if (properties.isSystemToolsEnabled()) {
            ToolkitHolder toolkit = toolkit();
            builder.toolkit(toolkit.toolkit());
            this.toolkitCatalog = new AgentToolCatalog(true, toolkit.catalog());
        }
        try {
            return builder.build();
        } catch (Exception e) {
            throw new BizException("Agent 运行时装配失败：" + e.getMessage());
        }
    }

    private AgentToolCatalog catalog(HarnessRunRequest request) {
        if (!request.toolCallingEnabled()) {
            return AgentToolCatalog.disabled();
        }
        return new AgentToolCatalog(true, toolkitCatalog.permissions(),
                new java.util.LinkedHashSet<>(request.allowedToolNames()));
    }

    private ToolkitHolder toolkit() {
        io.agentscope.core.tool.Toolkit toolkit = new io.agentscope.core.tool.Toolkit();
        Map<String, String> nameToPermission = new LinkedHashMap<>();
        // 同平台名工具（如 wiki.search 的管理端/公开端两实现）确定性消解：
        // 有权限码的实现保留安全名，无权限码的公开实现派生「_public」别名，
        // 运行次经 allowedToolNames 白名单精确选择，互不越权。
        List<AiAgentTool> tools = new ArrayList<>();
        aiAgentToolProvider.stream().forEach(tools::add);
        tools.sort(java.util.Comparator.comparing(
                (AiAgentTool tool) -> StringUtils.hasText(tool.descriptor().permissionCode()) ? 0 : 1));
        Map<String, Integer> nameCounts = new LinkedHashMap<>();
        for (AiAgentTool tool : tools) {
            AgentToolAdapter adapter = new AgentToolAdapter(tool, permissionGateway, toolExecutionGuard);
            String platformName = adapter.platformToolName();
            int seen = nameCounts.merge(platformName, 1, Integer::sum);
            String modelName = adapter.getName();
            if (seen > 1) {
                modelName = StringUtils.hasText(adapter.permissionCode())
                        ? modelName + "_" + seen
                        : modelName + "_public";
            }
            if (!modelName.equals(adapter.getName())) {
                adapter = new AgentToolAdapter(tool, permissionGateway, toolExecutionGuard, modelName);
            }
            toolkit.registerAgentTool(adapter);
            nameToPermission.put(adapter.getName(), adapter.permissionCode());
        }
        return new ToolkitHolder(toolkit, Map.copyOf(nameToPermission));
    }

    private record ToolkitHolder(io.agentscope.core.tool.Toolkit toolkit, Map<String, String> catalog) {
    }

    private Path workspaceDir() {
        Path workspace = Path.of(properties.getWorkspaceDir());
        try {
            Files.createDirectories(workspace);
        } catch (Exception e) {
            throw new BizException("Agent 工作区目录创建失败：" + e.getMessage());
        }
        return workspace;
    }

    private String defaultSystemPrompt() {
        return "你是 YuDreamAdmin 的 AI 助手，请使用中文清晰、准确地回答用户问题。"
                + "正文必须使用标准 Markdown：段落之间保留空行，列表、标题、引用和代码块使用规范语法；"
                + "不要输出未闭合的 Markdown 标记。";
    }

    private Model defaultModel() {
        ResolvedAiModel resolved = providerConfigParser.resolve(Map.of(), null, null, providerAdapters);
        return models.resolve(resolved);
    }

    private AgentModelSelection selection(HarnessRunRequest request) {
        if (!StringUtils.hasText(request.providerCode()) && !StringUtils.hasText(request.modelCode())) {
            return null;
        }
        if (!StringUtils.hasText(request.providerCode()) || !StringUtils.hasText(request.modelCode())) {
            throw new BizException("请选择可用的 AI 模型");
        }
        ResolvedAiModel resolved = providerConfigParser.resolve(
                config(request), request.providerCode(), request.modelCode(), providerAdapters);
        GenerateOptions options = resolved.adapter().generateOptions(
                resolved.provider(), resolved.model(), new AiGenerationRequest(
                        "", "", (String) null, List.<String>of(),
                        request.providerCode(), request.modelCode(),
                        config(request), List.<AiChatMessage>of(), false,
                        online.yudream.base.domain.platform.ai.enumerate.AiToolMode.NONE,
                        online.yudream.base.domain.platform.ai.valobj.AiStructuredOutput.none()));
        String modelKey = AgentScopeModels.identityOf(resolved.provider(), resolved.model());
        return new AgentModelSelection(models.resolve(resolved), options, modelKey);
    }

    private Map<String, String> config(HarnessRunRequest request) {
        return request.config() == null ? Map.of() : request.config();
    }

    private String userId(HarnessRunRequest request) {
        return request.userId() == null ? properties.getAnonymousUserId() : String.valueOf(request.userId());
    }

    private List<Msg> inputMessages(HarnessRunRequest request) {
        List<Msg> messages = new ArrayList<>();
        for (AiChatMessage item : request.history()) {
            if (!StringUtils.hasText(item.content())) {
                continue;
            }
            messages.add(item.isAssistant()
                    ? new AssistantMessage("assistant", item.content().trim())
                    : new UserMessage("user", item.content().trim()));
        }
        List<ContentBlock> blocks = new ArrayList<>();
        if (StringUtils.hasText(request.userPrompt())) {
            blocks.add(TextBlock.builder().text(request.userPrompt()).build());
        }
        for (String image : request.imageDataUrls()) {
            blocks.add(imageBlock(image));
        }
        if (blocks.isEmpty()) {
            throw new BizException("问题不能为空");
        }
        messages.add(new UserMessage("user", blocks.toArray(ContentBlock[]::new)));
        return messages;
    }

    private static DataBlock imageBlock(String imageDataUrl) {
        String value = imageDataUrl == null ? "" : imageDataUrl.trim();
        int comma = value.indexOf(',');
        if (!value.startsWith("data:") || comma <= 0) {
            throw new BizException("样图格式无效：仅支持 data URL");
        }
        String mimeType = value.substring(5, comma).split(";")[0];
        try {
            java.util.Base64.getDecoder().decode(value.substring(comma + 1));
        } catch (Exception e) {
            throw new BizException("样图解析失败：" + e.getMessage());
        }
        return DataBlock.builder()
                .source(io.agentscope.core.message.Base64Source.builder()
                        .mediaType(mimeType)
                        .data(value.substring(comma + 1))
                        .build())
                .build();
    }

    // ------------------------------------------------------------------
    // 事件消费：文本/思考增量与 token 用量
    // ------------------------------------------------------------------

    private AiUsage consume(Flux<AgentEvent> events, StringBuilder content, StringBuilder reasoning,
                            List<PendingToolConfirmation> pending) {
        List<ChatUsage> usages = Collections.synchronizedList(new ArrayList<>());
        try {
            events.doOnNext(event -> {
                        if (event instanceof TextBlockDeltaEvent delta && StringUtils.hasText(delta.getDelta())) {
                            content.append(delta.getDelta());
                            log.trace("Harness delta received, length={}", delta.getDelta().length());
                        } else if (event instanceof ThinkingBlockDeltaEvent thinking
                                && StringUtils.hasText(thinking.getDelta())) {
                            reasoning.append(thinking.getDelta());
                        } else if (event instanceof ModelCallEndEvent modelEnd && modelEnd.getUsage() != null) {
                            usages.add(modelEnd.getUsage());
                        } else if (event instanceof RequireUserConfirmEvent confirm) {
                            // HITL：工具校验 ASK → 收集待确认调用；框架持久化后等待
                            // 下一轮携带 ConfirmResult 的恢复调用
                            for (ToolUseBlock block : confirm.getToolCalls()) {
                                pending.add(new PendingToolConfirmation(
                                        block.getId(),
                                        block.getName(),
                                        block.getInput() == null ? Map.of() : block.getInput(),
                                        "该工具需要用户确认后才能执行"));
                            }
                        }
                    })
                    .blockLast(aiClientProperties.getReadTimeout());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted() || hasCause(e, InterruptedException.class)) {
                Thread.currentThread().interrupt();
                throw new BizException("AI 流式调用已取消");
            }
            log.debug("Harness agent run failed", e);
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage().replaceAll("\\s+", " ").trim();
            throw new BizException("AI 生成异常：" + (message.length() > 500 ? message.substring(0, 500) + "..." : message));
        }
        return totalUsage(usages);
    }

    private static AiUsage totalUsage(List<ChatUsage> usages) {
        long prompt = 0;
        long completion = 0;
        for (ChatUsage usage : usages) {
            prompt += usage.getInputTokens();
            completion += usage.getOutputTokens();
        }
        return new AiUsage(prompt, completion, prompt + completion);
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
