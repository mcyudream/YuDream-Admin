package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.service.AgentAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope;
import online.yudream.base.application.platform.ai.service.AiRunAppService;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSessionRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderConfigParser;
import online.yudream.base.plugin.spi.system.ai.PluginAiChatRequest;
import online.yudream.base.plugin.spi.system.ai.PluginAiChatResponse;
import online.yudream.base.plugin.spi.system.ai.PluginAiService;
import online.yudream.base.plugin.spi.system.ai.PluginAiRunHandle;
import online.yudream.base.plugin.spi.system.ai.PluginAiRunSnapshot;
import online.yudream.base.plugin.spi.system.ai.PluginAiInteractionRequiredException;
import online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext;
import online.yudream.base.domain.platform.ai.service.HarnessAgentGateway;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest;
import online.yudream.base.infra.platform.ai.runtime.ToolSchemas;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class PluginAiFrameworkService implements PluginAiService {
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final ObjectProvider<AiGenerationGateway> gatewayProvider;
    private final ObjectProvider<online.yudream.base.domain.platform.ai.service.AiAgentTool> systemToolProvider;
    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final PluginAiToolRegistry pluginAiToolRegistry;
    private final AiProviderConfigParser providerConfigParser;
    private final AgentAppService agentAppService;
    private final ObjectProvider<AiRunAppService> runs;
    private QqSandboxSessionRepo sandboxSessions;
    private ObjectProvider<HarnessAgentGateway> harnessProvider;

    @Autowired
    void setHarnessProvider(ObjectProvider<HarnessAgentGateway> harnessProvider) {
        this.harnessProvider = harnessProvider;
    }

    @Autowired
    void setSandboxSessions(QqSandboxSessionRepo sandboxSessions) {
        this.sandboxSessions = sandboxSessions;
    }

    @Override
    public List<online.yudream.base.plugin.spi.system.ai.PluginAiAgentOption> agents() {
        return agentAppService.publishedApplications().stream()
                .map(item -> new online.yudream.base.plugin.spi.system.ai.PluginAiAgentOption(
                        item.getCode(), item.getName(), item.getDescription()
                ))
                .toList();
    }

    @Override
    public CompletionStage<PluginAiChatResponse> runAgent(String agentCode, PluginAiChatRequest request) {
        return runAgentStream(agentCode, request, null);
    }

    @Override
    public CompletionStage<PluginAiChatResponse> runAgentStream(
            String agentCode, PluginAiChatRequest request, Consumer<String> onDelta) {
        return legacy(startAgent(agentCode, request, onDelta));
    }

    @Override
    public PluginAiRunHandle startAgent(String agentCode, PluginAiChatRequest request) {
        return startAgent(agentCode, request, null);
    }

    private PluginAiRunHandle startAgent(String agentCode, PluginAiChatRequest request, Consumer<String> onDelta) {
        return execute(request, "plugin-agent", emitter -> agentResponse(agentAppService.debugByCode(
                agentCode, agentCommand(request, PluginAiToolExecutionScope.current()),
                online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource.PLUGIN,
                node -> emitter.progress("agent-node", node),
                delta -> { emitter.content(delta); observe(onDelta, delta); },
                emitter::reasoning,
                emitter::tool,
                progress -> emitter.progress(progress.action(), progress)
        )));
    }

    private AgentRunCmd agentCommand(
            PluginAiChatRequest request,
            online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext executionContext
    ) {
        AgentRunCmd command = new AgentRunCmd();
        command.setUserId(executionContext == null ? null : executionContext.userId());
        command.setSessionId(nativeSession(request, executionContext));
        command.setInput(request.userPrompt());
        command.setRuntimeSystemPrompt(request.systemPrompt());
        command.setProviderCode(request.providerCode());
        command.setModelCode(request.modelCode());
        command.setRuntimeToolCallingEnabled(request.toolCallingEnabled());
        command.setRuntimeToolCodes(executionContext == null ? List.of() : executionContext.allowedToolNames());
        command.setHistory(request.history().stream()
                .map(item -> new AiChatMessage(item.role(), item.content()))
                .toList());
        command.setPermissionCodes(executionContext == null ? List.of() : executionContext.permissions());
        command.setPermissionContextExplicit(true);
        return command;
    }

    private PluginAiChatResponse agentResponse(
            online.yudream.base.application.platform.agent.dto.AgentRunDTO result
    ) {
        return new PluginAiChatResponse(
                result.getContent(),
                result.getToolResults().stream()
                        .map(item -> new online.yudream.base.plugin.spi.system.ai.PluginAiToolResult(
                                item.action(), item.message(), item.payload()
                        ))
                        .toList()
        );
    }

    @Override
    public List<online.yudream.base.plugin.spi.system.ai.PluginAiProviderOption> providers() {
        Map<String, String> config = capabilityModuleRepo.findByCode("ai").map(item -> item.getConfig() == null ? Map.<String, String>of() : item.getConfig()).orElse(Map.of());
        return providerConfigParser.parse(config).stream().filter(item -> item.enabled()).map(item -> new online.yudream.base.plugin.spi.system.ai.PluginAiProviderOption(item.code(), item.name(), item.models().stream().filter(model -> "chat".equalsIgnoreCase(model.kind())).map(model -> new online.yudream.base.plugin.spi.system.ai.PluginAiModelOption(model.optionCode(), model.name())).toList())).toList();
    }

    @Override
    public List<online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor> tools() {
        List<online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor> tools = new java.util.ArrayList<>(
                pluginAiToolRegistry.tools().stream().map(online.yudream.base.plugin.spi.system.ai.PluginAiTool::descriptor)
                        .filter(java.util.Objects::nonNull).toList());
        // 宿主公开原生工具（无平台权限码，当前为 wiki.search 公开检索）同样暴露给插件勾选，
        // 勾选后名称经 allowedToolNames 传入运行时工具注入通道，由工作流模型节点解析。
        java.util.Set<String> pluginToolNames = tools.stream()
                .map(online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor::name)
                .collect(java.util.stream.Collectors.toSet());
        systemToolProvider.stream()
                .map(online.yudream.base.domain.platform.ai.service.AiAgentTool::descriptor)
                .filter(java.util.Objects::nonNull)
                .filter(descriptor -> descriptor.permissionCode() == null || descriptor.permissionCode().isBlank())
                .filter(descriptor -> !pluginToolNames.contains(descriptor.name()))
                .map(descriptor -> new online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor(
                        descriptor.name(),
                        descriptor.title(),
                        descriptor.description(),
                        "",
                        online.yudream.base.plugin.spi.system.ai.PluginAiToolRisk.READ,
                        false,
                        java.util.Set.of("MENTION", "RANDOM"),
                        descriptor.inputSchema() == null ? Map.of() : descriptor.inputSchema()
                ))
                .forEach(tools::add);
        return List.copyOf(tools);
    }

    @Override
    public CompletionStage<PluginAiChatResponse> chat(PluginAiChatRequest request) {
        return chatStream(request, null, null);
    }

    @Override
    public CompletionStage<PluginAiChatResponse> chatStream(
            PluginAiChatRequest request, Consumer<String> onDelta,
            Consumer<online.yudream.base.plugin.spi.system.ai.PluginAiToolResult> onTool) {
        return legacy(startChat(request, onDelta, onTool));
    }

    @Override
    public PluginAiRunHandle startChat(PluginAiChatRequest request) {
        return startChat(request, null, null);
    }

    private PluginAiRunHandle startChat(PluginAiChatRequest request, Consumer<String> onDelta,
            Consumer<online.yudream.base.plugin.spi.system.ai.PluginAiToolResult> onTool) {
        return execute(request, "plugin-ai", emitter -> {
            if (!capabilityModuleRepo.findByCode("ai").map(item -> Boolean.TRUE.equals(item.getEnabled())).orElse(false)) {
                throw new BizException("AI 能力未启用");
            }
            Map<String, String> config = capabilityModuleRepo.findByCode("ai")
                    .map(item -> item.getConfig() == null ? Map.<String, String>of() : item.getConfig()).orElse(Map.of());
            Consumer<online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult> tools = tool -> {
                emitter.tool(tool);
                observe(onTool, new online.yudream.base.plugin.spi.system.ai.PluginAiToolResult(
                        tool.action(), tool.message(), tool.payload()));
            };
            if (request.toolCallingEnabled() || request.sessionId() != null && !request.sessionId().isBlank()) {
                HarnessAgentGateway harness = harnessProvider == null ? null : harnessProvider.getIfAvailable();
                if (harness == null) throw new BizException("Agent 会话服务不可用");
                var context = PluginAiToolExecutionScope.current();
                var names = nativeToolNames(context);
                emitter.allowNativeResume();
                var result = harness.run(new HarnessRunRequest(context == null ? null : context.userId(),
                                nativeSession(request, context), "plugin-chat", request.systemPrompt(), request.userPrompt(), List.of(),
                                request.providerCode(), request.modelCode(), config, request.toolCallingEnabled() && !names.isEmpty(),
                                context == null ? List.of() : context.permissions(),
                                request.history().stream().map(item -> new AiChatMessage(item.role(), item.content())).toList(), names),
                        delta -> { emitter.content(delta); observe(onDelta, delta); }, emitter::reasoning, tools,
                        progress -> emitter.progress(progress.action(), progress));
                return new PluginAiChatResponse(result.content(), result.toolResults().stream()
                        .map(tool -> new online.yudream.base.plugin.spi.system.ai.PluginAiToolResult(
                                tool.action(), tool.message(), tool.payload())).toList());
            }
            AiGenerationGateway gateway = gatewayProvider.getIfAvailable();
            if (gateway == null) throw new BizException("AI 生成服务不可用");
            var result = gateway.generateStream(new AiGenerationRequest(request.systemPrompt(), request.userPrompt(), null,
                            request.providerCode(), request.modelCode(), config,
                            request.history().stream().map(item -> new AiChatMessage(item.role(), item.content())).toList(),
                            request.toolCallingEnabled()),
                    delta -> { emitter.content(delta); observe(onDelta, delta); },
                    emitter::reasoning,
                    tools,
                    progress -> emitter.progress(progress.action(), progress));
            return new PluginAiChatResponse(result.summary(), toolResults(result));
        });
    }

    /** 所有插件入口复用持久运行出口；观察回调断开不影响服务端完成和额度结算。 */
    private PluginAiRunHandle execute(
            PluginAiChatRequest request, String module,
            java.util.function.Function<AiRunAppService.RunEmitter, PluginAiChatResponse> action) {
        CompletableFuture<PluginAiChatResponse> completion = new CompletableFuture<>();
            if (request == null) throw new BizException("AI 请求不能为空");
            var executionContext = withPermissions(request);
            Long userId = executionContext == null ? null : executionContext.userId();
            String sessionId = request.sessionId() != null && !request.sessionId().isBlank()
                    ? request.sessionId() : executionContext == null ? null : executionContext.traceId();
            AiRunAppService runner = runs.getIfAvailable();
            if (runner == null) throw new BizException("项目未开放 AI 能力");
            Long owner = userId == null ? 0L : userId;
            var originalSandbox = QqSandboxExecutionScope.requireActive();
            var run = runner.start(owner, module, sessionId, emitter -> {
                try {
                    emitter.input(request.userPrompt());
                    PluginAiChatResponse response = sandboxAware(request, originalSandbox, () -> {
                        var previous = PluginAiToolExecutionScope.current();
                        PluginAiToolExecutionScope.set(executionContext);
                        try (AiUsageExecutionScope ignored = AiUsageExecutionScope.open(userId)) {
                            emitter.captureInvocationContext();
                            return action.apply(emitter);
                        } finally {
                            if (previous == null) PluginAiToolExecutionScope.clear();
                            else PluginAiToolExecutionScope.set(previous);
                        }
                    }).get();
                    var phase = new PluginAiChatResponse(response.content(), response.toolResults(), emitter.id(),
                            emitter.awaitingInteraction() ? "WAITING" : "COMPLETED");
                    completion.complete(phase);
                    return phase;
                } catch (RuntimeException error) {
                    completion.completeExceptionally(error);
                    throw error;
                }
            }, () -> completion.cancel(true));
            completion.whenComplete((result, error) -> {
                if (completion.isCancelled()) runner.cancel(owner, run.id());
            });
            return new PluginAiRunHandle(run.id(), QqSandboxExecutionScope.track(completion));
    }

    private static CompletionStage<PluginAiChatResponse> legacy(PluginAiRunHandle handle) {
        // 保留显式 future.cancel 的服务端取消语义，不让 thenApply 的衍生 future 丢失取消。
        CompletableFuture<PluginAiChatResponse> result = new CompletableFuture<>();
        handle.result().whenComplete((phase, error) -> {
            if (error != null) result.completeExceptionally(error);
            else if ("WAITING".equals(phase.status())) result.completeExceptionally(new PluginAiInteractionRequiredException(handle.runId()));
            else result.complete(phase);
        });
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) handle.result().toCompletableFuture().cancel(true);
        });
        return result;
    }

    private static String nativeSession(PluginAiChatRequest request, PluginAiExecutionContext context) {
        if (request.sessionId() == null || request.sessionId().isBlank()) return null;
        if (request.sessionId().length() > 512) throw new BizException("插件 AI 会话标识过长");
        // 匿名外部用户共享框架 actor，必须再以真实触发来源隔离原生 Session/长期记忆。
        StringBuilder identity = new StringBuilder();
        for (String value : new String[]{context == null ? null : context.platformUserId(),
                context == null ? null : context.connectionId(), context == null ? null : context.channelId(), request.sessionId()}) {
            identity.append(value == null ? -1 : value.length()).append(':').append(value == null ? "" : value);
        }
        try {
            return "plugin-session:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(identity.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    @Override
    public PluginAiRunSnapshot getRun(String runId, PluginAiExecutionContext context) {
        if (context == null) throw new BizException("查询插件 AI 任务需要原调用上下文");
        var runner = runs.getIfAvailable();
        if (runner == null) throw new BizException("项目未开放 AI 能力");
        var expected = new online.yudream.base.domain.platform.ai.valobj.AiInvocationContextSnapshot(
                context.platformUserId(), context.connectionId(), context.channelId(), context.messageId(), context.trigger(),
                context.traceId(), List.of(), List.of(), null);
        var run = runner.getInvocationRun(context.userId() == null ? 0L : context.userId(), runId, expected);
        if (!java.util.Set.of("plugin-ai", "plugin-agent").contains(run.module())) throw new BizException("任务不属于插件 AI 入口");
        List<online.yudream.base.plugin.spi.system.ai.PluginAiToolResult> results = run.tools().stream()
                .filter(Map.class::isInstance).map(Map.class::cast)
                .map(tool -> new online.yudream.base.plugin.spi.system.ai.PluginAiToolResult(
                        String.valueOf(tool.getOrDefault("action", "")), String.valueOf(tool.getOrDefault("message", "")),
                        payload(tool.get("payload")))).toList();
        return new PluginAiRunSnapshot(run.id(), run.sessionId(), run.status().name(), String.valueOf(run.cursor()),
                run.content(), run.reasoning(), results, run.error(), run.resumeSupported(), phaseResponse(run, results));
    }

    private static PluginAiChatResponse phaseResponse(online.yudream.base.application.platform.ai.dto.AiRunDTO run,
            List<online.yudream.base.plugin.spi.system.ai.PluginAiToolResult> tools) {
        if (run.result() == null) return null;
        // 业务完成值与 UI 全程正文不同：多节点工作流应消费最终 content，而非拼接中间节点。
        String content = run.content();
        if (run.result() instanceof Map<?, ?> result) {
            if (result.get("content") instanceof String value) content = value;
            else if (result.get("summary") instanceof String value) content = value;
        } else if (run.result() instanceof PluginAiChatResponse result) content = result.content();
        return new PluginAiChatResponse(content, tools, run.id(), run.status().name());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Object value) { return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(); }

    private List<String> nativeToolNames(PluginAiExecutionContext context) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        if (context != null) pluginAiToolRegistry.tools().stream()
                .map(online.yudream.base.plugin.spi.system.ai.PluginAiTool::descriptor)
                .filter(tool -> context.allowsTool(tool.name()) && context.hasPermission(tool.permissionCode())
                        && tool.allowedTriggers().contains(context.trigger())
                        && (tool.risk() == online.yudream.base.plugin.spi.system.ai.PluginAiToolRisk.READ || context.grantsWriteTool(tool.name())))
                .map(tool -> ToolSchemas.safeName(tool.name())).forEach(names::add);
        var systemTools = systemToolProvider.stream().map(online.yudream.base.domain.platform.ai.service.AiAgentTool::descriptor).toList();
        var privilegedNames = systemTools.stream().filter(tool -> tool.permissionCode() != null && !tool.permissionCode().isBlank())
                .map(online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor::name).collect(java.util.stream.Collectors.toSet());
        for (var tool : systemTools) {
            if (context != null && !context.allowsTool(tool.name())) continue;
            boolean isPublic = tool.permissionCode() == null || tool.permissionCode().isBlank();
            if (!isPublic && (context == null || !context.hasPermission(tool.permissionCode()))) continue;
            String name = ToolSchemas.safeName(tool.name());
            if (isPublic && privilegedNames.contains(tool.name())) name += "_public";
            names.add(name);
        }
        return List.copyOf(names);
    }

    private static <T> void observe(Consumer<T> observer, T value) {
        if (observer == null) return;
        try { observer.accept(value); }
        catch (RuntimeException error) { log.debug("插件 AI 观察回调已断开，服务端继续保留进度", error); }
    }

    private static List<online.yudream.base.plugin.spi.system.ai.PluginAiToolResult> toolResults(
            online.yudream.base.domain.platform.ai.valobj.AiGenerationResult result) {
        if (result.toolResults() == null) {
            return List.of();
        }
        return result.toolResults().stream()
                .map(item -> new online.yudream.base.plugin.spi.system.ai.PluginAiToolResult(
                        item.action(), item.message(), item.payload()))
                .toList();
    }

    private <T> Supplier<T> sandboxAware(PluginAiChatRequest request,
            online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession originalSandbox, Supplier<T> action) {
        String connectionId = request == null || request.executionContext() == null
                ? null : request.executionContext().connectionId();
        return () -> {
            boolean sandboxConnection = connectionId != null && connectionId.startsWith("devtools-sandbox:");
            var session = sandboxSessions == null ? null : originalSandbox != null
                    ? sandboxSessions.findById(originalSandbox.id()).orElse(null)
                    : connectionId == null ? null : sandboxSessions.findByConnectionId(connectionId).orElse(null);
            if (originalSandbox != null && (session == null || !session.connectionId().equals(originalSandbox.connectionId())
                    || connectionId != null && !connectionId.equals(session.connectionId()))) throw new BizException("原 QQ 沙箱已不存在或触发连接不匹配");
            if (sandboxConnection && session == null) throw new BizException("原 QQ 沙箱已不存在或已过期，不能回落真实连接");
            if (session != null) {
                    try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
                        QqSandboxExecutionScope.requireActive();
                        return action.get();
                    }
            }
            return action.get();
        };
    }

    private online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext withPermissions(PluginAiChatRequest request) {
        var context = request.executionContext();
        if (context == null || context.userId() == null) {
            log.info("[YuDreamAdmin] plugin AI execution without bound user: platformUserId={}, tools may be filtered by permission",
                    context == null ? null : context.platformUserId());
            if (context == null) return null;
            return new online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext(null, context.platformUserId(),
                    context.connectionId(), context.channelId(), context.messageId(), context.trigger(), context.traceId(),
                    List.of(), context.allowedToolNames(), context.grantedWriteToolNames());
        }
        List<String> permissions = userRepo.findById(context.userId()).map(user -> roleRepo.findByIds(user.getRoles().stream()
                        .map(item -> item.getValue()).toList()).stream().flatMap(role -> role.getPermissions().stream())
                .map(item -> item.getCode()).distinct().toList()).orElse(List.of());
        if (permissions.isEmpty()) {
            log.info("[YuDreamAdmin] plugin AI execution permissions resolved empty: userId={}, plugin tools requiring permission will be filtered", context.userId());
        }
        return new online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext(context.userId(), context.platformUserId(), context.connectionId(), context.channelId(), context.messageId(), context.trigger(), context.traceId(), permissions, context.allowedToolNames(), context.grantedWriteToolNames());
    }
}
