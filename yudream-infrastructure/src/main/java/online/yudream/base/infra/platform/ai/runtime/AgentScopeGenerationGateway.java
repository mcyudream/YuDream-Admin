package online.yudream.base.infra.platform.ai.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.service.AgentToolExecutionGuard;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.service.AiGenerationObserverScope;
import online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope;
import online.yudream.base.domain.platform.ai.service.AiUsageQuotaGateway;
import online.yudream.base.domain.platform.ai.enumerate.AiToolMode;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.ai.valobj.AiUsageReservation;
import online.yudream.base.infra.platform.ai.service.AiClientProperties;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderAdapter;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderConfigParser;
import online.yudream.base.infra.platform.ai.service.provider.ResolvedAiModel;
import online.yudream.base.infra.platform.plugin.service.PluginAiToolExecutionScope;
import online.yudream.base.infra.platform.plugin.service.PluginAiToolRegistry;
import online.yudream.base.plugin.spi.system.ai.PluginAiTool;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolRisk;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 基于 AgentScope 的生成网关：替代旧 OpenAiCompatibleGenerationGateway 的全部手搓底层
 * （每请求建模型、流式拆包、工具循环、结构化输出降级、重试）。供应商差异由
 * {@link AiProviderAdapter#generateOptions} 产出的 {@link GenerateOptions} 表达，
 * 推理-工具循环交给框架 ReActAgent，模型连接由 {@link AgentScopeModels} 缓存复用。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AgentScopeGenerationGateway implements AiGenerationGateway {

    private static final String AGENT_NAME = "yudream-gateway";

    private final AgentScopeModels models;
    private final AiProviderConfigParser providerConfigParser;
    private final List<AiProviderAdapter> providerAdapters;
    private final AiClientProperties aiClientProperties;
    private final ObjectProvider<AiAgentTool> aiAgentToolProvider;
    private final PluginAiToolRegistry pluginAiToolRegistry;
    private AgentToolExecutionGuard toolExecutionGuard = AgentToolExecutionGuard.ALLOW_ALL;
    private AiUsageQuotaGateway usageQuotaGateway;

    // 额度网关在运行期才调用；@Lazy 代理避免经 ChatQuotaAppService→CapabilityAppService→providers 形成构造期循环。
    @Autowired(required = false)
    void setUsageQuotaGateway(@Lazy AiUsageQuotaGateway usageQuotaGateway) {
        this.usageQuotaGateway = usageQuotaGateway;
    }

    @Autowired(required = false)
    void setToolExecutionGuard(AgentToolExecutionGuard toolExecutionGuard) {
        this.toolExecutionGuard = toolExecutionGuard == null ? AgentToolExecutionGuard.ALLOW_ALL : toolExecutionGuard;
    }

    public AgentScopeGenerationGateway(
            AgentScopeModels models,
            AiProviderConfigParser providerConfigParser,
            List<AiProviderAdapter> providerAdapters,
            AiClientProperties aiClientProperties,
            ObjectProvider<AiAgentTool> aiAgentToolProvider,
            PluginAiToolRegistry pluginAiToolRegistry
    ) {
        this.models = models;
        this.providerConfigParser = providerConfigParser;
        this.providerAdapters = providerAdapters;
        this.aiClientProperties = aiClientProperties;
        this.aiAgentToolProvider = aiAgentToolProvider;
        this.pluginAiToolRegistry = pluginAiToolRegistry;
    }

    @Override
    public AiGenerationResult generate(AiGenerationRequest request) {
        return generateStream(request, null, null, null, null);
    }

    @Override
    public AiGenerationResult generateStream(
            AiGenerationRequest request,
            Consumer<String> onDelta,
            Consumer<AiAgentToolResult> onTool,
            Consumer<AiGenerationProgress> onProgress
    ) {
        return generateStream(request, onDelta, null, onTool, onProgress);
    }

    @Override
    public AiGenerationResult generateStream(
            AiGenerationRequest request,
            Consumer<String> onDelta,
            Consumer<String> onReasoningDelta,
            Consumer<AiAgentToolResult> onTool,
            Consumer<AiGenerationProgress> onProgress
    ) {
        var observation = AiGenerationObserverScope.capture(request);
        if (observation != null) {
            if (onDelta == null) onDelta = observation::content;
            if (onReasoningDelta == null) onReasoningDelta = observation::reasoning;
            if (onTool == null) onTool = observation::tool;
            if (onProgress == null) onProgress = observation::progress;
        }
        Generation generation = prepare(request, onTool, onProgress,
                new HarnessRunEvents(onDelta, onReasoningDelta, onProgress));
        AiUsageReservation reservation = reserve(request);
        if (reservation != null) generation.events().onModelStarted(() -> usageQuotaGateway.markStarted(reservation));
        progress(onProgress, "request", "正在准备模型请求。");
        try {
            if (generation.structuredSchema() != null) {
                Msg reply = generation.agent()
                        .call(generation.messages(), generation.structuredSchema(), generation.context())
                        .block(generation.timeout());
                progress(onProgress, "complete", "AI 处理完成。");
                return finish(reply, generation, true);
            }
            if (generation.agent() != null) {
                AgentRun run = new AgentRun(generation, onDelta, onReasoningDelta);
                run.run();
                progress(onProgress, "complete", "AI 处理完成。");
                return toResult(run.content(), generation.toolResults(), run.usage());
            }
            DirectRun run = new DirectRun(onDelta, onReasoningDelta, onProgress);
            run.subscribe(generation);
            progress(onProgress, "complete", "AI 处理完成。");
            return toResult(run.content(), generation.toolResults(), run.usage());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw translate("AI 流式调用异常", e);
        } finally {
            settle(generation, reservation);
        }
    }

    private AiUsageReservation reserve(AiGenerationRequest request) {
        return usageQuotaGateway == null ? null : usageQuotaGateway.reserve(
                AiUsageExecutionScope.currentUserId(), HarnessAgentRuntime.quotaBudget(
                        request.config() == null ? Map.of() : request.config()));
    }

    private void settle(Generation generation, AiUsageReservation reservation) {
        try {
            if (generation.agent() != null) {
                generation.agent().close();
            }
        } finally {
            if (usageQuotaGateway != null && reservation != null) {
                if (generation.events().hasUnknownUsage()) usageQuotaGateway.uncertain(reservation, generation.events().usage());
                else usageQuotaGateway.settle(reservation, generation.events().usage());
            }
        }
    }

    public void test(Map<String, String> config, String message) {
        generate(new AiGenerationRequest(
                "你是 YuDream AI 能力连通性测试助手，只需要返回一句简短中文。",
                message,
                null,
                null,
                null,
                config
        ));
    }

    // ------------------------------------------------------------------
    // 装配
    // ------------------------------------------------------------------

    private Generation prepare(AiGenerationRequest request, Consumer<AiAgentToolResult> onTool,
                               Consumer<AiGenerationProgress> onProgress, HarnessRunEvents events) {
        Map<String, String> config = request.config() == null ? Map.of() : request.config();
        ResolvedAiModel resolved = providerConfigParser.resolve(config, request.providerCode(), request.modelCode(), providerAdapters);
        validate(resolved);
        if (request.toolCallingEnabled() && request.toolMode() == AiToolMode.REQUIRED
                && !resolved.adapter().supportsRequiredToolChoice(resolved.provider(), resolved.model())) {
            throw new BizException("当前 AI 提供者不支持 REQUIRED 工具调用模式，请选择 AUTO 或支持原生 tool_choice 的模型");
        }
        GenerateOptions options = resolved.adapter()
                .generateOptions(resolved.provider(), resolved.model(), request);
        Model model = new MeteredAgentScopeModel(models.resolve(resolved), events);
        List<AiAgentToolResult> toolResults = Collections.synchronizedList(new ArrayList<>());
        List<ToolSchema> registered = List.of();
        Toolkit toolkit = null;
        boolean wantsTools = request.toolCallingEnabled() && request.toolMode() != AiToolMode.NONE;
        if (wantsTools) {
            List<Object> tools = scopedTools(toolResults, onTool, onProgress);
            boolean interactive = tools.stream().anyMatch(tool -> tool instanceof AgentToolAdapter adapter && adapter.requiresPersistentInteractions()
                    || tool instanceof PluginAiToolAdapter pluginAdapter && pluginAdapter.requiresPersistentInteractions());
            if (interactive) throw new BizException("审批或外部执行工具需要持久 Agent 会话，请使用 Agent 工作流或会话入口");
            if (!tools.isEmpty()) {
                Toolkit callToolkit = new Toolkit();
                tools.forEach(tool -> callToolkit.registerAgentTool((io.agentscope.core.tool.AgentTool) tool));
                toolkit = callToolkit;
            }
        }
        JsonNode structuredSchema = structuredSchema(request);
        events.structuredToolDeltas(structuredSchema != null);
        // 结构化输出必须走 Agent 结构化路径（框架负责原生/合成工具降级），因此即便无工具也装配 Agent。
        ReActAgent agent = toolkit != null || structuredSchema != null
                ? buildAgent(resolved, options, model, toolkit, request.systemPrompt(), events)
                : null;
        List<Msg> messages = agent == null ? messages(request) : conversationMessages(request);
        log.debug("AI call start, provider={}, endpoint={}, model={}, promptLength={}, images={}, tools={}, structured={}",
                resolved.provider().code(),
                resolved.provider().endpointUrl(),
                resolved.model().modelName(),
                length(request.userPrompt()),
                request.imageDataUrls().size(),
                toolkit == null ? 0 : toolkit.getToolSchemas().size(),
                request.structuredOutput().mode());
        var collector = new HarnessToolCollector(result -> {
            toolResults.add(result);
            if (onTool != null) onTool.accept(result);
        });
        RuntimeContext context = RuntimeContext.builder().put(HarnessToolCollector.class, collector).build();
        return new Generation(resolved, options, model, toolResults, agent, messages, structuredSchema, timeout(), onProgress, events, context);
    }

    private ReActAgent buildAgent(ResolvedAiModel resolved, GenerateOptions options, Model model, Toolkit toolkit,
                                  String systemPrompt, HarnessRunEvents events) {
        ReActAgent.Builder builder = ReActAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(systemPrompt == null ? "" : systemPrompt.trim())
                .model(model)
                .maxIters(16)
                .maxRetries(2)
                .middleware(events)
                .permissionContext(PermissionContextState.builder()
                        .mode(PermissionMode.BYPASS)
                        .build());
        if (options != null) {
            builder.generateOptions(options);
        }
        if (toolkit != null) {
            builder.toolkit(toolkit);
        }
        return builder.build();
    }

    private void validate(ResolvedAiModel resolved) {
        if (!StringUtils.hasText(resolved.provider().apiKey())) {
            throw new BizException("AI API Key 未配置：" + resolved.provider().displayName());
        }
        if (!StringUtils.hasText(resolved.provider().endpointBaseUrl())) {
            throw new BizException("AI API 地址未配置：" + resolved.provider().displayName());
        }
        if (!StringUtils.hasText(resolved.model().modelName())) {
            throw new BizException("AI 模型未配置：" + resolved.provider().displayName());
        }
    }

    private JsonNode structuredSchema(AiGenerationRequest request) {
        var structured = request.structuredOutput();
        if (structured.mode() != online.yudream.base.domain.platform.ai.valobj.AiStructuredOutput.Mode.JSON_SCHEMA) {
            return null;
        }
        return ToolResultJson.MAPPER.valueToTree(structured.schema());
    }

    private List<Msg> messages(AiGenerationRequest request) {
        List<Msg> messages = new ArrayList<>();
        String systemPrompt = request.systemPrompt() == null ? "" : request.systemPrompt().trim();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(new io.agentscope.core.message.SystemMessage("system", systemPrompt));
        }
        messages.addAll(conversationMessages(request));
        return messages;
    }

    /**
     * Agent 路径的对话消息：系统提示词经 builder.sysPrompt 注入（框架禁止消息列表携带 SYSTEM）。
     */
    private List<Msg> conversationMessages(AiGenerationRequest request) {
        List<Msg> messages = new ArrayList<>();
        for (AiChatMessage item : request.history()) {
            if (!StringUtils.hasText(item.content())) {
                continue;
            }
            messages.add(item.isAssistant()
                    ? new AssistantMessage("assistant", item.content().trim())
                    : new UserMessage("user", item.content().trim()));
        }
        List<ContentBlock> userBlocks = new ArrayList<>();
        userBlocks.add(TextBlock.builder().text(request.userPrompt()).build());
        for (String image : request.imageDataUrls()) {
            userBlocks.add(imageBlock(image));
        }
        messages.add(new UserMessage("user", userBlocks.toArray(ContentBlock[]::new)));
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
            Base64.getDecoder().decode(value.substring(comma + 1));
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
    // 工具作用域（与旧网关语义一致：工作流节点作用域 → 插件作用域 → 全量 Bean）
    // ------------------------------------------------------------------

    private List<Object> scopedTools(List<AiAgentToolResult> toolResults,
                                     Consumer<AiAgentToolResult> onTool,
                                     Consumer<AiGenerationProgress> onProgress) {
        List<Object> tools = new ArrayList<>();
        List<AiAgentTool> scopedTools = AiAgentToolExecutionScope.currentTools();
        if (scopedTools != null) {
            for (AiAgentTool tool : scopedTools) {
                tools.add(adapter(tool, toolResults, onTool));
            }
            var pluginScope = PluginAiToolExecutionScope.current();
            if (pluginScope != null) {
                java.util.Set<String> scopedNames = scopedTools.stream()
                        .map(tool -> tool.descriptor().name())
                        .collect(java.util.stream.Collectors.toSet());
                pluginAiToolRegistry.tools().stream()
                        .filter(tool -> tool.descriptor() != null && !scopedNames.contains(tool.descriptor().name()))
                        .filter(tool -> pluginGranted(tool, pluginScope))
                        .forEach(tool -> tools.add(pluginAdapter(tool, pluginScope, toolResults, onTool)));
            }
            return tools;
        }
        var scope = PluginAiToolExecutionScope.current();
        if (scope != null) {
            pluginAiToolRegistry.tools().stream()
                    .filter(tool -> pluginGranted(tool, scope))
                    .forEach(tool -> tools.add(pluginAdapter(tool, scope, toolResults, onTool)));
            return tools;
        }
        var allowedToolNames = AiAgentToolExecutionScope.current();
        aiAgentToolProvider.stream()
                .filter(tool -> allowedToolNames == null || allowedToolNames.contains(tool.descriptor().name()))
                .forEach(tool -> tools.add(adapter(tool, toolResults, onTool)));
        return tools;
    }

    private AgentToolAdapter adapter(AiAgentTool tool, List<AiAgentToolResult> toolResults,
                                     Consumer<AiAgentToolResult> onTool) {
        return new AgentToolAdapter(tool, permissionGateway(), toolExecutionGuard);
    }

    private PluginAiToolAdapter pluginAdapter(PluginAiTool tool, online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext scope,
                                              List<AiAgentToolResult> toolResults, Consumer<AiAgentToolResult> onTool) {
        return new PluginAiToolAdapter(tool, scope, toolExecutionGuard);
    }

    private boolean pluginGranted(PluginAiTool tool, online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext context) {
        var descriptor = tool.descriptor();
        boolean riskOk = descriptor != null
                && (descriptor.risk() == PluginAiToolRisk.READ || context.grantsWriteTool(descriptor.name()));
        return descriptor != null && riskOk
                && context.allowsTool(descriptor.name())
                && descriptor.allowedTriggers().contains(context.trigger())
                && context.hasPermission(descriptor.permissionCode());
    }

    private online.yudream.base.domain.platform.agent.service.AgentPermissionGateway permissionGateway() {
        // 网关路径（工作流节点/插件调用）沿用「调用入口已裁决」的快照语义：权限码由
        // AgentToolExecutor/PluginAiFrameworkService 在解析工具清单时校验，执行层只看沙箱守卫。
        return permissionCode -> true;
    }

    // ------------------------------------------------------------------
    // 运行
    // ------------------------------------------------------------------

    private AiGenerationResult finish(Msg reply, Generation generation, boolean structured) {
        if (reply == null) {
            return toResult("", generation.toolResults(), AiUsage.empty());
        }
        String content = reply.getTextContent();
        if (structured && reply.hasStructuredData()) {
            content = ToolSchemas.resultJson(reply.getStructuredData(false));
        }
        AiUsage usage = generation.events().usage();
        return toResult(content, generation.toolResults(), usage.totalTokens() > 0
                ? usage : usageOf(reply.getUsage()));
    }

    private static AiUsage usageOf(ChatUsage usage) {
        if (usage == null) {
            return AiUsage.empty();
        }
        long prompt = usage.getInputTokens();
        long completion = usage.getOutputTokens();
        long total = usage.getTotalTokens() > 0 ? usage.getTotalTokens() : prompt + completion;
        return new AiUsage(prompt, completion, total);
    }

    static AiGenerationResult toResult(String content, List<AiAgentToolResult> toolResults, AiUsage usage) {
        if (toolResults != null && !toolResults.isEmpty()) {
            String summary = StringUtils.hasText(content) && !content.isBlank()
                    ? content.trim()
                    : toolResults.stream().findFirst().map(AiAgentToolResult::message).orElse("画布操作已完成。");
            return AiGenerationResult.of(summary, toolResults, usage);
        }
        return AiGenerationResult.of(content == null ? "" : content.trim(), List.of(), usage);
    }

    private BizException translate(String prefix, Exception e) {
        if (Thread.currentThread().isInterrupted() || hasCause(e, InterruptedException.class)) {
            Thread.currentThread().interrupt();
            return new BizException("AI 流式调用已取消");
        }
        log.debug("AI call failed", e);
        String message = e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage().replaceAll("\\s+", " ").trim();
        if (message.length() > 500) {
            message = message.substring(0, 500) + "...";
        }
        if (hasCause(e, java.util.concurrent.TimeoutException.class)) {
            long minutes = Math.max(1, aiClientProperties.getReadTimeout().toMinutes());
            return new BizException(prefix + "：模型生成超过 " + minutes + " 分钟仍未结束，请缩小任务范围后重试");
        }
        return new BizException(prefix + "：" + message);
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

    private static void progress(Consumer<AiGenerationProgress> onProgress, String action, String content) {
        if (onProgress != null) {
            onProgress.accept(new AiGenerationProgress(action, content));
        }
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private Duration timeout() {
        return aiClientProperties.getReadTimeout();
    }

    /**
     * Agent 路径的流式消费：文本/思考增量与工具回调全部来自框架事件。
     */
    private final class AgentRun {
        private final Generation generation;

        AgentRun(Generation generation, Consumer<String> onDelta, Consumer<String> onReasoningDelta) {
            this.generation = generation;
        }

        void run() {
            generation.agent().streamEvents(generation.messages(), generation.context())
                    .blockLast(generation.timeout());
        }

        String content() {
            return generation.events().content();
        }

        AiUsage usage() {
            return generation.events().usage();
        }
    }

    /**
     * 直连模型路径（无工具、无结构化）：直接消费 ChatResponse 流。
     */
    private final class DirectRun {
        private final Consumer<String> onDelta;
        private final Consumer<String> onReasoningDelta;
        private final Consumer<AiGenerationProgress> onProgress;
        private final StringBuilder content = new StringBuilder();
        private final java.util.concurrent.atomic.AtomicReference<ChatUsage> usage =
                new java.util.concurrent.atomic.AtomicReference<>();

        DirectRun() {
            this(null, null, null);
        }

        DirectRun(Consumer<String> onDelta, Consumer<String> onReasoningDelta, Consumer<AiGenerationProgress> onProgress) {
            this.onDelta = onDelta;
            this.onReasoningDelta = onReasoningDelta;
            this.onProgress = onProgress;
        }

        void subscribe(Generation generation) {
            Flux<io.agentscope.core.model.ChatResponse> flux = generation.model()
                    .stream(generation.messages(), List.<ToolSchema>of(), generation.options());
            if (onProgress != null) {
                flux = flux.doFirst(() -> progress(onProgress, "subscribed", "模型流已建立，正在等待首个响应。"));
            }
            try {
                flux.doOnNext(chunk -> {
                        if (chunk.getUsage() != null && chunk.getUsage().getTotalTokens() > 0) {
                            usage.set(chunk.getUsage());
                        }
                        for (ContentBlock block : chunk.getContent()) {
                            if (block instanceof ThinkingBlock thinking && thinking.getThinking() != null && !thinking.getThinking().isEmpty()) {
                                if (onReasoningDelta != null) {
                                    onReasoningDelta.accept(thinking.getThinking());
                                }
                            } else if (block instanceof TextBlock text && text.getText() != null && !text.getText().isEmpty()) {
                                if (content.isEmpty() && onProgress != null) {
                                    progress(onProgress, "first-delta", "模型已开始输出内容。");
                                }
                                content.append(text.getText());
                                if (onDelta != null) {
                                    onDelta.accept(text.getText());
                                }
                            }
                        }
                    })
                    .blockLast(generation.timeout());
            } finally {
                generation.events().recordUsage(usage.get());
            }
        }

        String content() {
            return content.toString();
        }

        AiUsage usage() {
            ChatUsage chatUsage = usage.get();
            return chatUsage == null ? AiUsage.empty() : usageOf(chatUsage);
        }
    }

    private static final class ToolResultJson {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
                new com.fasterxml.jackson.databind.ObjectMapper();
    }

    private record Generation(
            ResolvedAiModel resolved,
            GenerateOptions options,
            Model model,
            List<AiAgentToolResult> toolResults,
            ReActAgent agent,
            List<Msg> messages,
            JsonNode structuredSchema,
            Duration timeout,
            Consumer<AiGenerationProgress> onProgress,
            HarnessRunEvents events,
            RuntimeContext context
    ) {
    }
}
