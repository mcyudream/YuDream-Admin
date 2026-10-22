package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.session.AgentSession;
import io.agentscope.harness.agent.session.SessionAnswer;
import io.agentscope.core.session.SessionMigration;
import io.agentscope.core.util.JsonUtils;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.filesystem.remote.RemoteFilesystem;
import io.agentscope.harness.agent.session.WorkspaceSessionLogStore;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.service.AgentPermissionGateway;
import online.yudream.base.domain.platform.agent.service.AgentToolExecutionGuard;
import online.yudream.base.domain.platform.agent.valobj.AgentToolPermissions;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.service.AiUsageQuotaGateway;
import online.yudream.base.domain.platform.ai.service.HarnessExecutionScope;
import online.yudream.base.domain.platform.ai.service.HarnessAgentGateway;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.ai.valobj.AiUsageReservation;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress;
import online.yudream.base.domain.platform.ai.valobj.HarnessSessionSnapshot;
import online.yudream.base.domain.platform.ai.valobj.HarnessInteractionAnswer;
import online.yudream.base.domain.platform.ai.valobj.HarnessPendingInteraction;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Harness 级 Agent 运行时：每次执行装配独立 Agent，原生 AgentSession 持有后台执行订阅，
 * 输入队列、执行日志与 checkpoint 经 Mongo 共享工作区持久化。RuntimeContext 注入可信身份、
 * 权限快照与按次工具；平台消息和 AiRun 表仅作 UI 投影。
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
    private final Map<String, java.util.Set<ActiveExecution>> activeSessions = new ConcurrentHashMap<>();
    private AiUsageQuotaGateway usageQuotaGateway;
    private online.yudream.base.domain.platform.ai.service.AiInvocationContextGateway invocationContexts;
    private online.yudream.base.infra.platform.plugin.service.PluginAiToolRegistry pluginTools;
    /** 系统工具模块服务：模块开关、按需加载计划与 load_tools 元工具；测试直接构造时为 null（全部开放）。 */
    private online.yudream.base.application.platform.ai.service.AiToolModuleAppService toolModules;
    /** MCP 工具桥接（能力未启用时为空）。 */
    private online.yudream.base.domain.platform.mcp.service.McpAgentToolProvider mcpTools;

    @Autowired(required = false)
    void setToolModules(online.yudream.base.application.platform.ai.service.AiToolModuleAppService toolModules) {
        this.toolModules = toolModules;
    }

    @Autowired(required = false)
    void setMcpTools(online.yudream.base.domain.platform.mcp.service.McpAgentToolProvider mcpTools) {
        this.mcpTools = mcpTools;
    }

    @Autowired(required = false)
    void setPluginTools(online.yudream.base.infra.platform.plugin.service.PluginAiToolRegistry tools) {
        this.pluginTools = tools;
    }

    @Autowired(required = false)
    void setInvocationContexts(online.yudream.base.domain.platform.ai.service.AiInvocationContextGateway contexts) {
        this.invocationContexts = contexts;
    }

    // 额度网关在运行期才调用；@Lazy 代理避免经 ChatQuotaAppService→CapabilityAppService→providers 形成构造期循环。
    @Autowired(required = false)
    void setUsageQuotaGateway(@Lazy AiUsageQuotaGateway usageQuotaGateway) {
        this.usageQuotaGateway = usageQuotaGateway;
    }

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
        return run(request, onDelta, onReasoningDelta, onTool, null);
    }

    @Override
    public HarnessRunResult run(HarnessRunRequest request, Consumer<String> onDelta,
                                Consumer<String> onReasoningDelta, Consumer<AiAgentToolResult> onTool,
                                Consumer<AiGenerationProgress> onProgress) {
        return execute(request, null, List.of(), onDelta, onReasoningDelta, onTool, onProgress);
    }

    @Override
    public HarnessRunResult resume(HarnessRunRequest request, String turnId, Consumer<String> onDelta,
                                   Consumer<String> onReasoningDelta, Consumer<AiAgentToolResult> onTool,
                                   Consumer<AiGenerationProgress> onProgress) {
        if (!StringUtils.hasText(request.sessionId()) || !StringUtils.hasText(turnId)) {
            throw new BizException("恢复任务需要会话和任务标识");
        }
        return execute(request, turnId, List.of(), onDelta, onReasoningDelta, onTool, onProgress);
    }

    @Override
    public HarnessRunResult respond(HarnessRunRequest request, List<HarnessInteractionAnswer> answers,
                                    Consumer<String> onDelta, Consumer<String> onReasoningDelta,
                                    Consumer<AiAgentToolResult> onTool, Consumer<AiGenerationProgress> onProgress) {
        if (!StringUtils.hasText(request.sessionId()) || answers == null || answers.isEmpty()) {
            throw new BizException("回复待办需要会话和真实答案");
        }
        return execute(request, null, List.copyOf(answers), onDelta, onReasoningDelta, onTool, onProgress);
    }

    @Override
    public boolean interrupt(Long userId, String sessionId, String code) {
        boolean interrupted = false;
        java.util.Set<ActiveExecution> sessions = activeSessions.get(sessionKey(
                userId == null ? properties.getAnonymousUserId() : String.valueOf(userId), sessionId,
                StringUtils.hasText(code) ? code : "general"));
        if (sessions != null) {
            for (ActiveExecution execution : sessions) {
                interrupted |= execution.session().interrupt();
                execution.agent().interrupt(execution.context());
                execution.control().interruptModel();
            }
        }
        return interrupted;
    }

    @Override
    public HarnessSessionSnapshot inspect(HarnessRunRequest request) {
        if (!StringUtils.hasText(request.sessionId())) {
            throw new BizException("检查任务需要会话标识");
        }
        try (HarnessAgent agent = buildAgent(agentCode(request), selection(request), toolkit(), null, null,
                request.userId() == null)) {
            RuntimeContext context = RuntimeContext.builder().userId(userId(request))
                    .sessionId(request.sessionId()).build();
            AgentSession session = agent.session(context);
            return snapshot(session);
        }
    }

    @Override
    public HarnessSessionSnapshot cancelPending(HarnessRunRequest request, String turnId) {
        if (!StringUtils.hasText(request.sessionId()) || !StringUtils.hasText(turnId)) {
            throw new BizException("取消待办需要会话和任务标识");
        }
        try (HarnessAgent agent = buildAgent(agentCode(request), selection(request), toolkit(), null, null,
                request.userId() == null)) {
            AgentSession session = agent.session(RuntimeContext.builder().userId(userId(request))
                    .sessionId(request.sessionId()).build());
            HarnessSessionSnapshot before = snapshot(session);
            if (!turnId.equals(before.turnId())) throw new BizException("只能取消当前会话的最新任务");
            if ("cancelled".equals(before.status())) return before;
            if (!List.of("suspended", "interrupted").contains(before.status())) {
                throw new BizException("任务尚未暂停，不能取消持久待办");
            }
            if (!before.uncertainToolCallIds().isEmpty()) {
                throw new BizException("工具结果尚未确认，不能自动取消或重试");
            }
            io.agentscope.core.session.SessionRecovery.cancelPending(session.log(), turnId);
            return snapshot(session);
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            throw new BizException("取消 Agent 待办失败：" + error.getMessage());
        }
    }

    private HarnessSessionSnapshot snapshot(AgentSession session) {
        var projection = session.inspect();
        var latest = io.agentscope.core.session.SessionTurns.read(session.log()).latest();
        List<HarnessPendingInteraction> interactions = session.pending().entrySet().stream()
                .filter(entry -> entry.getValue().data().get("call") != null)
                .map(entry -> {
                    ToolUseBlock call = JsonUtils.getJsonCodec().convertValue(entry.getValue().data().get("call"), ToolUseBlock.class);
                    return new HarnessPendingInteraction(entry.getKey(), String.valueOf(entry.getValue().data().get("kind")),
                            call.getName(), call.getInput());
                }).toList();
        List<PendingToolConfirmation> confirmations = interactions.stream()
                .filter(item -> "confirmation".equals(item.kind()))
                .map(item -> new PendingToolConfirmation(item.requestId(), item.toolName(), item.arguments(),
                        "该工具需要用户确认后才能执行")).toList();
        return new HarnessSessionSnapshot(projection.asOfSeq(), latest == null ? null : latest.turnId(),
                latest == null ? null : latest.runId(), latest == null ? "idle" : latest.status(), confirmations,
                projection.uncertainToolCalls().stream().sorted().toList(), interactions);
    }

    private HarnessRunResult execute(HarnessRunRequest request, String resumeTurnId,
                                     List<HarnessInteractionAnswer> answers, Consumer<String> onDelta,
                                     Consumer<String> onReasoningDelta, Consumer<AiAgentToolResult> onTool,
                                     Consumer<AiGenerationProgress> onProgress) {
        AgentModelSelection selection = selection(request);
        HarnessRunRequest preparedRequest = new HarnessRunRequest(request.userId(), request.sessionId(), request.agentCode(),
                request.systemPrompt(), request.userPrompt(), request.imageDataUrls(), selection.providerCode(),
                selection.modelCode(), request.config(), request.toolCallingEnabled(), request.permissionCodes(),
                request.history(), request.allowedToolNames(), request.confirmDecisions());
        var invocation = invocationContexts == null ? null : invocationContexts.capture();
        if (invocation == null) HarnessExecutionScope.reportRequest(preparedRequest);
        else try (var ignored = online.yudream.base.domain.platform.ai.service.AiInvocationContextScope.open(invocation)) {
            HarnessExecutionScope.reportRequest(preparedRequest);
        }
        ToolkitHolder tools = toolkit();
        // 系统工具按模块提供：常驻模块默认可⻅，其余模块经 load_tools 在本次运行内按需加载。
        // 调用方显式授权的 scoped 工具（CMS 画布/前端工具/插件）视为已授权，不做按需过滤；
        // 恢复/审批续跑时同样不做按需过滤（原 turn 已规划的工具必须仍可执行）。
        boolean resuming = resumeTurnId != null || !answers.isEmpty() || !request.confirmDecisions().isEmpty();
        online.yudream.base.domain.platform.ai.valobj.AiToolModulePlan modulePlan =
                toolModules == null || tools.scoped() ? null : toolModules.plan(tools.registeredTools());
        online.yudream.base.domain.platform.ai.valobj.AiToolLoadState loadState =
                modulePlan == null ? null : modulePlan.newLoadState(!resuming);
        if (modulePlan != null && modulePlan.onDemandActive()) {
            tools.toolkit().registerAgentTool(new LoadToolsTool(toolModules, modulePlan));
        }
        HarnessRunEvents events = new HarnessRunEvents(onDelta, onReasoningDelta, onProgress,
                HarnessExecutionScope.currentReferenceConsumer());
        String idempotencyKey = HarnessExecutionScope.currentIdempotencyKey();
        HarnessExecutionControl control = new HarnessExecutionControl();
        HarnessToolCollector collector = new HarnessToolCollector(onTool);
        String sessionId = request.sessionId() == null ? "once-" + UUID.randomUUID() : request.sessionId();
        var contextBuilder = RuntimeContext.builder()
                .userId(userId(request))
                .sessionId(sessionId)
                .put(AgentToolPermissions.class, AgentToolPermissions.explicit(request.permissionCodes()))
                .put(AgentToolCatalog.class, catalog(request, tools.catalog()))
                .put(AgentToolSchemaObserver.class, new AgentToolSchemaObserver(
                        HarnessExecutionScope.captureAllowedToolsReporter()))
                .put(AgentSystemPromptOverride.class, new AgentSystemPromptOverride(request.systemPrompt()))
                .put(HarnessToolCollector.class, collector);
        if (loadState != null) {
            contextBuilder.put(online.yudream.base.domain.platform.ai.valobj.AiToolLoadState.class, loadState);
        }
        RuntimeContext context = contextBuilder.build();
        AiUsageReservation reservation = usageQuotaGateway == null ? null
                : usageQuotaGateway.reserve(request.userId(), quotaBudget(config(request)));
        if (reservation != null) events.onModelStarted(() -> usageQuotaGateway.markStarted(reservation));
        String key = sessionKey(userId(request), sessionId, agentCode(request));
        HarnessRunResult result;
        try (HarnessAgent agent = buildAgent(agentCode(request), selection, tools, events, control,
                request.userId() == null || sandboxWorkspace(invocation))) {
            log.debug("[harness-step] agent built sessionId={}", sessionId);
            AgentSession session = agent.session(context);
            importLegacyState(session, request, sessionId);
            log.debug("[harness-step] session ready sessionId={}", sessionId);
            ActiveExecution execution = new ActiveExecution(agent, session, context, control);
            java.util.Set<ActiveExecution> live = activeSessions.computeIfAbsent(key,
                    ignored -> ConcurrentHashMap.newKeySet());
            live.add(execution);
            try {
                AgentSession.Task task = !answers.isEmpty() ? respondInteractions(session, answers)
                        : resumeTurnId != null ? session.resume(resumeTurnId)
                        : request.confirmDecisions().isEmpty() ? session.submit(
                                StringUtils.hasText(idempotencyKey) ? idempotencyKey : UUID.randomUUID().toString(),
                                inputMessages(request))
                        : respondConfirmations(session, request.confirmDecisions());
                log.debug("[harness-step] task submitted sessionId={} taskId={}", sessionId, task == null ? "null" : "ok");
                if (onProgress != null) {
                    onProgress.accept(new AiGenerationProgress("subscribed", "任务已持久接收。"));
                }
                // 启动观察窗口：提交后 onAgent 一直不触发，说明框架执行器没有消费任务
                // （实测于上一回合失败后的会话：submit 已落库但后台执行永不开始，且跨重启保持）。
                // 必须快速失败并重置会话日志，否则运行会占住会话直到 readTimeout（30 分钟）。
                AgentSession.Task finished;
                try {
                    finished = session.await(task).block(properties.getStartupTimeout());
                } catch (IllegalStateException startupTimeout) {
                    if (events.started()) {
                        // 已在真实执行只是超过了观察窗口：按完整读超时继续等。
                        finished = session.await(task).block(aiClientProperties.getReadTimeout());
                    }
                    else {
                        resetSessionLog(agentCode(request), request.userId(), sessionId);
                        throw new BizException("AI 会话执行无响应，已自动重置该会话的执行日志，请重新发送");
                    }
                }
                if (finished == null) {
                    finished = session.await(task).block(aiClientProperties.getReadTimeout());
                }
                events.awaitObserver();
                if (finished == null) {
                    throw new BizException("Agent 任务没有返回执行状态");
                }
                HarnessSessionSnapshot snapshot = snapshot(session);
                HarnessExecutionScope.reportState(snapshot);
                if ("failed".equals(finished.status()) || "rejected".equals(finished.status())) {
                    throw new BizException("AI 生成异常：" + (finished.error() == null
                            ? session.executionError() : finished.error()));
                }
                if ("interrupted".equals(finished.status()) || "cancelled".equals(finished.status())) {
                    throw new BizException("AI 流式调用已取消");
                }
                String content = events.started() ? events.content() : outputForTurn(session, finished.turnId());
                result = new HarnessRunResult(content, events.reasoning(), events.usage(),
                        collector.snapshot(), snapshot.pendingConfirmations(), finished.turnId(), finished.runId(),
                        finished.status(), snapshot.pendingInteractions());
            } catch (Exception error) {
                // await 是观察者。取消它不会停框架的后台任务；主动停止必须写入原生中断命令。
                try {
                    session.interrupt();
                    agent.interrupt(context);
                    control.interruptModel();
                } catch (Exception stopFailure) {
                    error.addSuppressed(stopFailure);
                }
                throw error;
            } finally {
                live.remove(execution);
                activeSessions.computeIfPresent(key, (ignored, current) -> current.isEmpty() ? null : current);
            }
        } catch (BizException error) {
            throw error;
        } catch (Exception error) {
            if (Thread.currentThread().isInterrupted() || hasCause(error, InterruptedException.class)) {
                Thread.currentThread().interrupt();
                throw new BizException("AI 流式调用已取消");
            }
            log.debug("Harness session run failed", error);
            throw new BizException("AI 生成异常：" + error.getMessage());
        } finally {
            if (usageQuotaGateway != null && reservation != null) {
                if (events.hasUnknownUsage()) usageQuotaGateway.uncertain(reservation, events.usage());
                else usageQuotaGateway.settle(reservation, events.usage());
            }
        }
        // close 会等待框架的记忆维护排空，返回时包括这些真实辅助调用的用量。
        return new HarnessRunResult(result.content(), result.reasoning(), events.usage(), result.toolResults(),
                result.pendingConfirmations(), result.nativeTurnId(), result.nativeRunId(), result.status(),
                result.pendingInteractions());
    }

    /**
     * HITL 恢复：把用户的批准/拒绝决定包装为携带 METADATA_CONFIRM_RESULTS 的消息
     * 交回 agent 续跑（框架按 toolCallId 匹配暂挂的 ASK 工具调用，approved=false
     * 时以权限拒绝反馈给模型继续推理）。
     */
    private AgentSession.Task respondConfirmations(AgentSession session, List<ToolConfirmationDecision> decisions) {
        Map<String, SessionAnswer> answers = new LinkedHashMap<>();
        Map<String, io.agentscope.core.session.SessionEvent> pending = session.pending();
        for (ToolConfirmationDecision decision : decisions) {
            String requestId = pending.entrySet().stream().filter(entry -> {
                Object call = entry.getValue().data().get("call");
                if (call == null) {
                    return false;
                }
                ToolUseBlock block = JsonUtils.getJsonCodec().convertValue(call, ToolUseBlock.class);
                return decision.toolCallId() != null && decision.toolCallId().equals(block.getId());
            }).map(Map.Entry::getKey).findFirst().orElseThrow(() -> new BizException("没有待确认的工具调用"));
            answers.put(requestId, new SessionAnswer.Approval(decision.approved(), null));
        }
        return session.respond(answers);
    }

    private AgentSession.Task respondInteractions(AgentSession session, List<HarnessInteractionAnswer> responses) {
        Map<String, SessionAnswer> answers = new LinkedHashMap<>();
        var pending = session.pending();
        for (HarnessInteractionAnswer response : responses) {
            var request = pending.get(response.requestId());
            if (request == null) {
                throw new BizException("待办不存在或已经处理");
            }
            String kind = String.valueOf(request.data().get("kind"));
            if ("confirmation".equals(kind) && response.approved() != null) {
                answers.put(response.requestId(), new SessionAnswer.Approval(response.approved(), null));
            } else if ("external_execution".equals(kind) && response.output() != null) {
                answers.put(response.requestId(), new SessionAnswer.Output(List.of(TextBlock.builder()
                        .text(JsonUtils.getJsonCodec().toJson(response.output())).build())));
            } else {
                throw new BizException("待办答案类型不匹配");
            }
        }
        return session.respond(answers);
    }

    private void importLegacyState(AgentSession session, HarnessRunRequest request, String sessionId) {
        if (session.log().head().seq() != 0) {
            return;
        }
        // 保留历史快照，不把平台 UI 消息当作模型上下文重新拼装；只迁入框架存过的状态。
        stateStore.get(userId(request), sessionId, "agent_state", io.agentscope.core.state.AgentState.class)
                .ifPresent(state -> SessionMigration.importBaseline(session.log(), state,
                        "platformAgentState", 0));
    }

    private String outputForTurn(AgentSession session, String turnId) {
        String content = "";
        for (var event : session.log().scan(0, session.log().head().seq())) {
            if ("turn/output".equals(event.type()) && java.util.Objects.equals(turnId, event.turnId())
                    && event.data().get("message") != null) {
                Msg message = JsonUtils.getJsonCodec().convertValue(event.data().get("message"), Msg.class);
                content = message.getTextContent();
            }
        }
        return content;
    }

    private static String sessionKey(String userId, String sessionId, String code) {
        return code + ":" + userId + ":" + sessionId;
    }

    /**
     * 框架会话执行器在上一回合失败后可能拒绝消费新任务（submit 已落库但后台执行永不开始，
     * 且状态持久在会话日志里，重启无效）。清空该会话的原生日志，让下一次提交从干净会话开始。
     * 平台侧聊天/调试记录不受影响，仅丢失该会话的模型侧上下文与记忆引用。
     */
    private void resetSessionLog(String agentCode, Long userId, String sessionId) {
        if (!properties.isResetSessionOnStartupTimeout()) {
            return;
        }
        try {
            // 框架按 s_ + base64url（无填充）编码 agent/user/session 三段构造日志键。
            var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
            List<String> ns = List.of("yudream", "agent-session-history", "__agentscope_session_log_v1__",
                    "agents", "s_" + encoder.encodeToString(agentCode.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    "sessions", "s_" + encoder.encodeToString(String.valueOf(userId == null ? "" : userId)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    "s_" + encoder.encodeToString(sessionId.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            int removed = 0;
            for (var item : baseStore.search(ns, 10_000, 0)) {
                baseStore.delete(ns, item.key());
                removed++;
            }
            log.warn("已重置无响应的 Agent 会话日志：agent={} sessionId={} entries={}", agentCode, sessionId, removed);
        } catch (Exception error) {
            log.warn("重置 Agent 会话日志失败：agent={} sessionId={}", agentCode, sessionId, error);
        }
    }

    private record ActiveExecution(HarnessAgent agent, AgentSession session, RuntimeContext context,
                                   HarnessExecutionControl control) { }

    static long quotaBudget(Map<String, String> config) {
        try {
            return Math.max(1, Math.min(1_000_000, Long.parseLong(config.getOrDefault("maxTokens", "4096"))));
        } catch (NumberFormatException ignored) {
            return 4096;
        }
    }

    /**
     * 工作区文件系统：USER 隔离；工作区 profile 额外路由 cms/** 到 KV，
     * 供 CMS 静态站点文件树（pages/assets）与 IO 端点共享。
     */
    private RemoteFilesystemSpec workspaceSpec(String agentCode, boolean anonymous) {
        RemoteFilesystemSpec spec = new RemoteFilesystemSpec(baseStore)
                .isolationScope(anonymous ? IsolationScope.SESSION : IsolationScope.USER)
                .anonymousUserId(properties.getAnonymousUserId());
        if (workspaceProfile(agentCode)) {
            spec.addSharedPrefix("cms/");
        }
        return spec;
    }

    /** 沙箱的真实绑定用户仍参与身份/额度，模拟文件与长期记忆只进入该会话命名空间。 */
    private boolean sandboxWorkspace(online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot captured) {
        var sandbox = online.yudream.base.infra.platform.plugin.service.QqSandboxExecutionScope.requireActive();
        if (captured != null && StringUtils.hasText(captured.sandboxId())) {
            if (sandbox == null || !captured.sandboxId().equals(sandbox.id())) {
                throw new BizException("原 QQ 沙箱上下文未建立，不能使用真实用户工作区");
            }
            return true;
        }
        return sandbox != null;
    }

    private String agentCode(HarnessRunRequest request) {
        return StringUtils.hasText(request.agentCode()) ? request.agentCode() : "general";
    }

    /** 拥有文件工作区的 agent profile 前缀：工作流与 CMS 静态站点 builder。 */
    static boolean workspaceProfile(String agentCode) {
        return agentCode != null && agentCode.startsWith("workflow");
    }

    /**
     * 模型连接缓存复用；Agent 的可变运行、工具和技能状态每次执行独立。
     */
    HarnessAgent buildAgent(String code, AgentModelSelection selection) {
        return buildAgent(code, selection, false);
    }

    HarnessAgent buildAgent(String code, AgentModelSelection selection, boolean anonymous) {
        return buildAgent(code, selection, toolkit(), null, null, anonymous);
    }

    private HarnessAgent buildAgent(String code, AgentModelSelection selection,
                                    ToolkitHolder tools, HarnessRunEvents events, HarnessExecutionControl control,
                                    boolean anonymous) {
        Model model = selection == null || selection.model() == null ? defaultModel() : selection.model();
        if (events != null) {
            model = new MeteredAgentScopeModel(model, events);
        }
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
                // CompositeFilesystem 的默认 sessionStorage 仍是本机文件，原生日志显式使用共享 CAS 后端。
                .sessionLogStore(new WorkspaceSessionLogStore(new RemoteFilesystem(baseStore,
                        List.of("yudream", "agent-session-history"))))
                .workspace(workspaceDir())
                .filesystem(workspaceSpec(code, anonymous || sandboxWorkspace(null)))
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
        if (selection != null && selection.options() != null) {
            builder.generateOptions(selection.options());
        }
        // 聊天型 Agent 不暴露宿主文件系统与 shell；工作流/CMS builder 工作区 profile
        // 启用文件工具（read/write/edit/list/grep/glob），shell 任何 profile 都不开放。
        if (!workspaceProfile(code)) {
            builder.disableFilesystemTools();
        }
        builder.disableShellTool();
        // 网络读取统一经宿主 web.fetch 工具和权限守卫；不装配默认无宿主授权的网络工具。
        builder.disableWebTools();
        if (!workspaceProfile(code) || !properties.isSubagentsEnabled()) {
            builder.disableSubagents();
            builder.disableDynamicSubagents();
        }
        builder.middleware(new PerCallSystemPromptMiddleware());
        builder.middleware(new PerCallToolFilterMiddleware(permissionGateway, toolExecutionGuard));
        if (events != null) {
            builder.middleware(events);
        }
        if (control != null) {
            builder.middleware(control);
        }
        if (!properties.isMemoryHooksEnabled()) {
            builder.disableMemoryHooks();
        }
        // 平台技能库（MongoDB）作为框架级统一技能源；workspace/<user>/skills 仍可按用户覆盖。
        // 对话中选择了技能时（运行线程上的选择作用域）只挂载所选技能。
        java.util.Set<String> selectedSkills = online.yudream.base.domain.platform.ai.service.AiSkillSelectionScope.current();
        builder.skillRepository(selectedSkills == null ? skillRepository : skillRepository.selected(selectedSkills));
        if (properties.isSystemToolsEnabled()) {
            builder.toolkit(tools.toolkit());
        }
        try {
            return builder.build();
        } catch (Exception e) {
            log.error("Agent 运行时装配失败：code={}", code, e);
            throw new BizException("Agent 运行时装配失败：" + e.getMessage());
        }
    }

    private AgentToolCatalog catalog(HarnessRunRequest request, Map<String, String> permissions) {
        if (!request.toolCallingEnabled()) {
            return AgentToolCatalog.disabled();
        }
        return new AgentToolCatalog(true, permissions,
                request.allowedToolNames().stream().map(ToolSchemas::safeName)
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)));
    }

    private ToolkitHolder toolkit() {
        io.agentscope.core.tool.Toolkit toolkit = new io.agentscope.core.tool.Toolkit();
        Map<String, String> nameToPermission = new LinkedHashMap<>();
        Map<String, AiAgentTool> registeredTools = new LinkedHashMap<>();
        // 同平台名工具（如 wiki.search 的管理端/公开端两实现）确定性消解：
        // 有权限码的实现保留安全名，无权限码的公开实现派生「_public」别名，
        // 运行次经 allowedToolNames 白名单精确选择，互不越权。
        List<AiAgentTool> tools = new ArrayList<>();
        List<AiAgentTool> globalTools = aiAgentToolProvider.stream().toList();
        java.util.Set<String> privilegedNames = globalTools.stream()
                .filter(tool -> StringUtils.hasText(tool.descriptor().permissionCode()))
                .map(tool -> tool.descriptor().name()).collect(java.util.stream.Collectors.toSet());
        List<AiAgentTool> scoped = AiAgentToolExecutionScope.currentTools();
        if (scoped == null) {
            // 全局工具集：按模块开关过滤（关闭模块的工具不注册、不进模型视野）；MCP 工具随模块提供。
            for (AiAgentTool tool : globalTools) {
                if (moduleEnabled(tool)) {
                    tools.add(tool);
                }
            }
            if (mcpTools != null) {
                for (AiAgentTool tool : mcpTools.tools()) {
                    if (tool != null && moduleEnabled(tool)) {
                        tools.add(tool);
                    }
                }
            }
        } else {
            for (AiAgentTool tool : scoped) {
                if (tool != null && moduleEnabled(tool)) {
                    tools.add(tool);
                }
            }
        }
        tools.sort(java.util.Comparator.comparing(
                (AiAgentTool tool) -> StringUtils.hasText(tool.descriptor().permissionCode()) ? 0 : 1));
        java.util.Set<String> selectedPluginNames = tools.stream().filter(AiAgentTool::pluginProvided)
                .map(tool -> tool.descriptor().name()).collect(java.util.stream.Collectors.toSet());
        Map<String, Integer> nameCounts = new LinkedHashMap<>();
        for (AiAgentTool tool : tools) {
            if (tool.pluginProvided()) continue;
            AgentToolAdapter adapter = new AgentToolAdapter(tool, permissionGateway, toolExecutionGuard);
            String platformName = adapter.platformToolName();
            int seen = nameCounts.merge(platformName, 1, Integer::sum);
            String modelName = adapter.getName();
            if (!StringUtils.hasText(adapter.permissionCode()) && privilegedNames.contains(platformName)) {
                modelName += "_public";
            } else if (seen > 1) {
                modelName = StringUtils.hasText(adapter.permissionCode())
                        ? modelName + "_" + seen
                        : modelName + "_public";
            }
            if (!modelName.equals(adapter.getName())) {
                adapter = new AgentToolAdapter(tool, permissionGateway, toolExecutionGuard, modelName);
            }
            toolkit.registerAgentTool(adapter);
            nameToPermission.put(adapter.getName(), adapter.permissionCode() == null ? "" : adapter.permissionCode());
            registeredTools.put(adapter.getName(), tool);
        }
        var pluginContext = online.yudream.base.infra.platform.plugin.service.PluginAiToolExecutionScope.current();
        if (!selectedPluginNames.isEmpty() && (pluginContext == null || pluginTools == null)) {
            throw new BizException("插件工具需要原插件触发上下文，不能脱离原授权会话执行");
        }
        java.util.Set<String> registeredPluginNames = new java.util.HashSet<>();
        if (pluginContext != null && pluginTools != null) {
            for (var tool : pluginTools.tools()) {
                if (scoped != null && !selectedPluginNames.contains(tool.descriptor().name())) continue;
                var adapter = new PluginAiToolAdapter(tool, pluginContext, toolExecutionGuard);
                if (!adapter.granted()) {
                    if (selectedPluginNames.contains(tool.descriptor().name())) throw new BizException("当前会话无权调用插件工具：" + tool.descriptor().name());
                    continue;
                }
                if (nameToPermission.containsKey(adapter.getName())) throw new BizException("插件与系统 AI 工具名称冲突：" + adapter.getName());
                String module = pluginModuleCode(tool);
                if (!moduleAllowed(module)) {
                    if (selectedPluginNames.contains(tool.descriptor().name())) {
                        throw new BizException("插件工具模块已关闭：" + module);
                    }
                    continue;
                }
                toolkit.registerAgentTool(adapter);
                String permission = tool.descriptor().permissionCode();
                nameToPermission.put(adapter.getName(), permission == null ? "" : permission);
                registeredTools.put(adapter.getName(), new PluginPlanTool(tool.descriptor(), module));
                registeredPluginNames.add(tool.descriptor().name());
            }
        }
        if (!registeredPluginNames.containsAll(selectedPluginNames)) throw new BizException("原工作流选择的插件工具当前不可用");
        return new ToolkitHolder(toolkit, Map.copyOf(nameToPermission), Map.copyOf(registeredTools),
                AiAgentToolExecutionScope.currentTools() != null);
    }

    /** 插件工具在模块计划中的只读投影：只提供名称/描述/风险/模块，不参与执行。 */
    private static final class PluginPlanTool implements AiAgentTool {
        private final online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor descriptor;
        private final online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk risk;
        private final boolean requiresConfirmation;
        private final String moduleCode;

        private PluginPlanTool(online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor source, String moduleCode) {
            this.descriptor = new online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor(
                    source.name(), source.title(), source.description(), source.permissionCode(),
                    source.title(), "插件工具", source.description(),
                    source.inputSchema() == null ? Map.of() : source.inputSchema());
            this.risk = source.risk() == online.yudream.base.plugin.spi.system.ai.PluginAiToolRisk.READ
                    ? online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk.READ
                    : online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk.WRITE;
            this.requiresConfirmation = source.requiresConfirmation()
                    || source.risk() == online.yudream.base.plugin.spi.system.ai.PluginAiToolRisk.DESTRUCTIVE;
            this.moduleCode = moduleCode;
        }

        @Override
        public online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk risk() {
            return risk;
        }

        @Override
        public boolean requiresConfirmation() {
            return requiresConfirmation;
        }

        @Override
        public String moduleCode() {
            return moduleCode;
        }

        @Override
        public online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult execute(
                online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall call) {
            throw new BizException("插件工具必须经插件适配器执行：" + descriptor.name());
        }
    }

    /** 工具模块是否开放（模块开关关闭的工具不注册，工具目录为空时视为开放）。 */
    private boolean moduleEnabled(AiAgentTool tool) {
        if (toolModules == null || tool == null) {
            return true;
        }
        return moduleAllowed(toolModules.moduleCodeOf(tool));
    }

    private boolean moduleAllowed(String moduleCode) {
        return toolModules == null || moduleCode == null || moduleCode.isBlank()
                || toolModules.settings().isModuleEnabled(moduleCode);
    }

    private String pluginModuleCode(Object tool) {
        if (tool instanceof AiAgentTool agentTool && agentTool.moduleCode() != null) {
            return agentTool.moduleCode();
        }
        return online.yudream.base.domain.platform.ai.valobj.AiToolModules.PLUGIN_PREFIX + "plugin";
    }

    private record ToolkitHolder(
            io.agentscope.core.tool.Toolkit toolkit,
            Map<String, String> catalog,
            Map<String, AiAgentTool> registeredTools,
            boolean scoped) {
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
        return new AgentModelSelection(models.resolve(resolved), options, modelKey,
                resolved.provider().code(), resolved.model().optionCode());
    }

    private Map<String, String> config(HarnessRunRequest request) {
        return request.config() == null ? Map.of() : request.config();
    }

    private String userId(HarnessRunRequest request) {
        return request.userId() == null ? properties.getAnonymousUserId() : String.valueOf(request.userId());
    }

    private List<Msg> inputMessages(HarnessRunRequest request) {
        List<Msg> messages = new ArrayList<>();
        // 有状态请求仅提交新输入，历史只用于明确的无状态入口。
        for (AiChatMessage item : request.sessionId() == null ? request.history() : List.<AiChatMessage>of()) {
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
