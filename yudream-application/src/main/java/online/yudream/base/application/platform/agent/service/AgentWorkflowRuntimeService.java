package online.yudream.base.application.platform.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.dto.AgentDebugEventDTO;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowEventListener;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowExecution;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowExecutor;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowGraphParser;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowInitialInput;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNode;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeHandler;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeResult;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowRuntimeResult;
import online.yudream.base.application.platform.agent.workflow.handler.AgentCitationNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentCodeNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentConditionNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentDocumentNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentEndNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentInputNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentKnowledgeNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentLlmNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentStartNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentTemplateNodeHandler;
import online.yudream.base.application.platform.agent.workflow.handler.AgentToolNodeHandler;
import online.yudream.base.application.platform.agent.workflow.support.AgentKnowledgeOperations;
import online.yudream.base.application.platform.agent.workflow.support.AgentModelToolResolver;
import online.yudream.base.application.platform.agent.workflow.support.AgentToolExecutor;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowRunState;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.agent.service.AgentPermissionGateway;
import online.yudream.base.domain.platform.agent.service.AgentToolExecutionGuard;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress;
import online.yudream.base.domain.platform.document.service.DocumentTextExtractor;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class AgentWorkflowRuntimeService {
    private final ObjectMapper objectMapper;
    private final RuntimeExecutor runtimeExecutor;
    private final AgentToolRepo toolRepo;
    private final ObjectProvider<AiGenerationGateway> generationGateways;
    private final ObjectProvider<online.yudream.base.domain.platform.ai.service.HarnessAgentGateway> harnessAgentGateways;
    private final ObjectProvider<AiAgentTool> systemToolProvider;
    private final AgentKnowledgeOperations knowledgeOperations;
    private final DocumentTextExtractor documentTextExtractor;
    private final ObjectProvider<online.yudream.base.domain.platform.agent.service.AgentPluginToolGateway> pluginToolGateways;
    private final AgentPermissionGateway permissionGateway;
    private final CapabilityAppService capabilityAppService;
    private final AgentExecutionTracer executionTracer;
    /** 系统工具模块服务（可选注入：单测直接构造时为空，视为全部开放）。 */
    private online.yudream.base.application.platform.ai.service.AiToolModuleAppService toolModules;
    /** MCP 工具桥接（可选注入）。 */
    private org.springframework.beans.factory.ObjectProvider<online.yudream.base.domain.platform.mcp.service.McpAgentToolProvider> mcpTools;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setToolModules(online.yudream.base.application.platform.ai.service.AiToolModuleAppService toolModules) {
        this.toolModules = toolModules;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setMcpTools(org.springframework.beans.factory.ObjectProvider<online.yudream.base.domain.platform.mcp.service.McpAgentToolProvider> mcpTools) {
        this.mcpTools = mcpTools;
    }

    /** MCP 工具提供方（未启用能力或未注入时为空）。 */
    private online.yudream.base.domain.platform.mcp.service.McpAgentToolProvider mcpToolProvider() {
        return mcpTools == null ? null : mcpTools.getIfAvailable();
    }
    private AgentToolExecutionGuard toolExecutionGuard = AgentToolExecutionGuard.ALLOW_ALL;
    private online.yudream.base.domain.platform.agent.repo.AgentWorkflowCheckpointRepo checkpoints;

    @Autowired(required = false)
    void setCheckpoints(online.yudream.base.domain.platform.agent.repo.AgentWorkflowCheckpointRepo checkpoints) {
        this.checkpoints = checkpoints;
    }

    @Autowired(required = false)
    void setToolExecutionGuard(AgentToolExecutionGuard toolExecutionGuard) {
        this.toolExecutionGuard = toolExecutionGuard == null ? AgentToolExecutionGuard.ALLOW_ALL : toolExecutionGuard;
    }

    public AgentWorkflowRuntimeResult execute(
            AgentApplication application,
            AgentRunCmd command,
            Map<String, String> aiConfig,
            Consumer<AgentDebugEventDTO> onNode,
            Consumer<String> onDelta,
            Consumer<AiAgentToolResult> onTool
    ) {
        return execute(application, command, aiConfig, onNode, onDelta, null, onTool);
    }

    public AgentWorkflowRuntimeResult execute(
            AgentApplication application,
            AgentRunCmd command,
            Map<String, String> aiConfig,
            Consumer<AgentDebugEventDTO> onNode,
            Consumer<String> onDelta,
            Consumer<String> onReasoningDelta,
            Consumer<AiAgentToolResult> onTool
    ) {
        return execute(application, command, aiConfig, onNode, onDelta, onReasoningDelta, onTool, null);
    }

    public AgentWorkflowRuntimeResult execute(
            AgentApplication application,
            AgentRunCmd command,
            Map<String, String> aiConfig,
            Consumer<AgentDebugEventDTO> onNode,
            Consumer<String> onDelta,
            Consumer<String> onReasoningDelta,
            Consumer<AiAgentToolResult> onTool,
            Consumer<AiGenerationProgress> onProgress
    ) {
        return execute(application, command, aiConfig, null, onNode, onDelta, onReasoningDelta, onTool, onProgress);
    }

    public AgentWorkflowRuntimeResult execute(
            AgentApplication application,
            AgentRunCmd command,
            Map<String, String> aiConfig,
            AgentTraceSource sourceHint,
            Consumer<AgentDebugEventDTO> onNode,
            Consumer<String> onDelta,
            Consumer<String> onReasoningDelta,
            Consumer<AiAgentToolResult> onTool,
            Consumer<AiGenerationProgress> onProgress
    ) {
        AgentTraceSession trace = executionTracer.begin(application, command, sourceHint);
        Consumer<String> tracedReasoningDelta = delta -> {
            trace.reasoningDelta(delta);
            if (onReasoningDelta != null) {
                onReasoningDelta.accept(delta);
            }
        };
        Consumer<AiAgentToolResult> tracedTool = tool -> {
            trace.toolResult(tool);
            if (onTool != null) {
                onTool.accept(tool);
            }
        };
        List<AiAgentTool> systemTools = systemToolProvider.stream().toList();
        Set<String> systemToolCodes = systemTools.stream()
                .map(tool -> tool.descriptor().name())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        AgentToolExecutor toolExecutor = new AgentToolExecutor(
                objectMapper, runtimeExecutor, toolRepo, systemTools, permissionGateway, pluginToolGateways,
                toolExecutionGuard, toolModules, mcpToolProvider()
        );
        AgentWorkflowRunState state = new AgentWorkflowRunState(
                application,
                command,
                aiConfig,
                systemToolCodes,
                onDelta,
                tracedReasoningDelta,
                tracedTool,
                onProgress,
                new AgentModelToolResolver(toolExecutor),
                trace.traceId()
        );
        AgentWorkflowValueResolver values = new AgentWorkflowValueResolver(objectMapper);
        AgentWorkflowGraphParser graphParser = new AgentWorkflowGraphParser(objectMapper);
        var graph = graphParser.parse(application.getWorkflowJson());
        ensurePythonRuntime(graph, systemToolCodes);
        AgentWorkflowExecutor executor = new AgentWorkflowExecutor(
                graphParser, handlers(values, application, state, toolExecutor)
        );
        String checkpointId = online.yudream.base.domain.platform.ai.service.HarnessExecutionScope.currentIdempotencyKey();
        try (var usageScope = online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope.open(
                command.getUserId() == null ? online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope.currentUserId() : command.getUserId())) {
            AgentWorkflowExecution execution = executor.execute(
                    application.getWorkflowJson(),
                    initialInput(command),
                    checkpointListener(listener(onNode, values, trace), checkpointId, application, command, state, trace)
            );
            if (execution.waiting()) {
                if (checkpointId == null || checkpoints == null) {
                    throw new online.yudream.base.domain.common.exception.BizException("工作流工具交互需要持久 AI 运行入口");
                }
                trace.suspend();
                return new AgentWorkflowRuntimeResult(execution.checkpoint().suspendedNative().content(),
                        state.reasoning(), state.toolResults(), state.usage());
            }
            Object output = graph.topologicalOrder().stream()
                    .filter(node -> "end".equals(node.kind()))
                    .filter(node -> execution.executedNodeIds().contains(node.id()))
                    .map(node -> execution.context().nodeOutput(node.id()))
                    .reduce((left, right) -> right)
                    .orElseThrow(() -> new online.yudream.base.domain.common.exception.BizException("工作流未执行结束节点"));
            AgentWorkflowRuntimeResult result = new AgentWorkflowRuntimeResult(content(output), state.reasoning(), state.toolResults(), state.usage());
            if (checkpoints != null && checkpointId != null) checkpoints.complete(checkpointId);
            trace.succeed(result);
            return result;
        } catch (RuntimeException e) {
            trace.fail(e);
            throw e;
        }
    }

    /** 只补入原生已完成节点的业务输出，原模型消息与已执行宿主节点都不重跑。 */
    public AgentWorkflowRuntimeResult continueAfterNative(
            online.yudream.base.domain.platform.agent.aggregate.AgentWorkflowCheckpoint checkpoint,
            AgentApplication application, AgentRunCmd command, Map<String, String> aiConfig,
            online.yudream.base.domain.platform.ai.valobj.HarnessRunResult nativeResult,
            Consumer<AgentDebugEventDTO> onNode, Consumer<String> onDelta, Consumer<String> onReasoning,
            Consumer<AiAgentToolResult> onTool, Consumer<AiGenerationProgress> onProgress) {
        var pending = checkpoint.frame().suspendedNative();
        if (pending == null || !"completed".equals(nativeResult.status())
                || !java.util.Objects.equals(pending.nativeTurnId(), nativeResult.nativeTurnId())) {
            throw new online.yudream.base.domain.common.exception.BizException("原生结果与暂停的工作流节点不匹配");
        }
        var systemTools = systemToolProvider.stream().toList();
        Set<String> systemCodes = systemTools.stream().map(tool -> tool.descriptor().name())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var toolExecutor = new AgentToolExecutor(objectMapper, runtimeExecutor, toolRepo, systemTools,
                permissionGateway, pluginToolGateways, toolExecutionGuard, toolModules, mcpToolProvider());
        var state = new AgentWorkflowRunState(application, command, aiConfig, systemCodes, onDelta, onReasoning,
                onTool, onProgress, new AgentModelToolResolver(toolExecutor), checkpoint.runKey());
        var tools = new ArrayList<>(checkpoint.toolResults()); tools.addAll(nativeResult.toolResults());
        state.restore(checkpoint.modelInvocations(), checkpoint.reasoning() + nativeResult.reasoning(),
                tools, checkpoint.usage().plus(nativeResult.usage()));
        var values = new AgentWorkflowValueResolver(objectMapper);
        var parser = new AgentWorkflowGraphParser(objectMapper);
        var graph = parser.parse(checkpoint.workflowJson());
        ensurePythonRuntime(graph, systemCodes);
        var nodeHandlers = handlers(values, application, state, toolExecutor);
        var pendingNode = graph.node(checkpoint.frame().pausedNodeId());
        var handler = nodeHandlers.stream().filter(candidate -> candidate.kind().equals(pendingNode.kind()))
                .filter(AgentLlmNodeHandler.class::isInstance).map(AgentLlmNodeHandler.class::cast)
                .findFirst().orElseThrow(() -> new online.yudream.base.domain.common.exception.BizException("暂停节点不再是可恢复的模型节点"));
        var completed = new online.yudream.base.domain.platform.ai.valobj.HarnessRunResult(
                pending.content() + nativeResult.content(), nativeResult.reasoning(), nativeResult.usage(), nativeResult.toolResults(),
                nativeResult.pendingConfirmations(), nativeResult.nativeTurnId(), nativeResult.nativeRunId(),
                nativeResult.status(), nativeResult.pendingInteractions());
        var trace = executionTracer.resume(checkpoint.runKey());
        try (var usageScope = online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope.open(command.getUserId())) {
            var execution = new AgentWorkflowExecutor(parser, nodeHandlers).resume(checkpoint.workflowJson(), checkpoint.frame(),
                    handler.completeNative(pendingNode, completed), checkpointListener(listener(onNode, values, trace),
                            checkpoint.runId(), application, command, state, trace));
            if (execution.waiting()) {
                trace.suspend();
                return new AgentWorkflowRuntimeResult(execution.checkpoint().suspendedNative().content(),
                        state.reasoning(), state.toolResults(), state.usage());
            }
            Object output = graph.topologicalOrder().stream().filter(node -> "end".equals(node.kind()))
                    .filter(node -> execution.executedNodeIds().contains(node.id()))
                    .map(node -> execution.context().nodeOutput(node.id())).reduce((left, right) -> right)
                    .orElseThrow(() -> new online.yudream.base.domain.common.exception.BizException("工作流未执行结束节点"));
            var result = new AgentWorkflowRuntimeResult(content(output), state.reasoning(), state.toolResults(), state.usage());
            checkpoints.complete(checkpoint.runId()); trace.succeed(result); return result;
        } catch (RuntimeException failure) { trace.fail(failure); throw failure; }
    }

    public List<AiAgentTool> restoreCheckpointTools(
            online.yudream.base.domain.platform.agent.aggregate.AgentWorkflowCheckpoint checkpoint,
            AgentApplication application, AgentRunCmd command) {
        var node = new AgentWorkflowGraphParser(objectMapper).parse(checkpoint.workflowJson()).node(checkpoint.frame().pausedNodeId());
        if ("NONE".equalsIgnoreCase(node.data().path("toolMode").asText())) return List.of();
        var systemTools = systemToolProvider.stream().toList();
        var executor = new AgentToolExecutor(objectMapper, runtimeExecutor, toolRepo, systemTools,
                permissionGateway, pluginToolGateways, toolExecutionGuard, toolModules, mcpToolProvider());
        List<String> codes = new ArrayList<>();
        if (node.data().path("toolCodes").isArray()) node.data().path("toolCodes").forEach(code -> codes.add(code.asText()));
        ensurePythonRuntime(new AgentWorkflowGraphParser(objectMapper).parse(checkpoint.workflowJson()),
                systemTools.stream().map(tool -> tool.descriptor().name()).collect(java.util.stream.Collectors.toSet()));
        return new AgentModelToolResolver(executor).resolve(codes, application, command);
    }

    private AgentWorkflowEventListener checkpointListener(AgentWorkflowEventListener delegate, String id,
            AgentApplication application, AgentRunCmd command, AgentWorkflowRunState state, AgentTraceSession trace) {
        return new AgentWorkflowEventListener() {
            @Override public void onNodeStarted(AgentWorkflowNode node, online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext context) {
                delegate.onNodeStarted(node, context);
            }
            @Override public void onNodeCompleted(AgentWorkflowNode node, AgentWorkflowNodeResult result,
                    online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext context) {
                delegate.onNodeCompleted(node, result, context);
            }
            @Override public void onNodeFailed(AgentWorkflowNode node, RuntimeException failure,
                    online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext context) {
                delegate.onNodeFailed(node, failure, context);
            }
            @Override public void onNodeSkipped(AgentWorkflowNode node,
                    online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext context) {
                delegate.onNodeSkipped(node, context);
            }
            @Override public void onNodeSuspended(AgentWorkflowNode node,
                    online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext context) {
                if (state != null) state.emitProgress(new AiGenerationProgress("waiting", "工作流正在等待工具交互：" + node.title()));
            }
            @Override public void onCheckpoint(online.yudream.base.domain.platform.agent.valobj.AgentWorkflowFrame frame) {
                if (id == null || checkpoints == null) return;
                checkpoints.save(online.yudream.base.application.platform.agent.assembler.AgentWorkflowCheckpointAssembler.capture(
                        id, application, command, state, frame, objectMapper));
                trace.suspend();
            }
        };
    }

    private List<AgentWorkflowNodeHandler> handlers(
            AgentWorkflowValueResolver values,
            AgentApplication application,
            AgentWorkflowRunState state,
            AgentToolExecutor toolExecutor
    ) {
        List<AgentWorkflowNodeHandler> handlers = new ArrayList<>();
        handlers.add(new AgentStartNodeHandler(values));
        handlers.add(new AgentInputNodeHandler(values));
        handlers.add(new AgentEndNodeHandler(values));
        handlers.add(new AgentConditionNodeHandler(values));
        handlers.add(new AgentTemplateNodeHandler(values));
        handlers.add(new AgentCodeNodeHandler(values, objectMapper, runtimeExecutor));
        handlers.add(new AgentDocumentNodeHandler(values, documentTextExtractor));
        handlers.add(new AgentCitationNodeHandler(values, objectMapper));
        handlers.add(new AgentToolNodeHandler(
                values, objectMapper, toolExecutor, application, state
        ));
        for (String kind : List.of("search", "vector", "rerank", "embedding")) {
            handlers.add(new AgentKnowledgeNodeHandler(kind, values, objectMapper, knowledgeOperations));
        }
        AiGenerationGateway generationGateway = generationGateways.getIfAvailable();
        var harnessAgents = harnessAgentGateways.getIfAvailable();
        if (generationGateway != null) {
            handlers.add(new AgentLlmNodeHandler("understand", values, objectMapper, generationGateway, harnessAgents, state));
            handlers.add(new AgentLlmNodeHandler("llm", values, objectMapper, generationGateway, harnessAgents, state));
            handlers.add(new AgentLlmNodeHandler("extract", values, objectMapper, generationGateway, harnessAgents, state));
            handlers.add(new AgentLlmNodeHandler("classify", values, objectMapper, generationGateway, harnessAgents, state));
            handlers.add(new AgentLlmNodeHandler("vision", values, objectMapper, generationGateway, harnessAgents, state));
        }
        return handlers;
    }

    private AgentWorkflowEventListener listener(
            Consumer<AgentDebugEventDTO> onNode,
            AgentWorkflowValueResolver values,
            AgentTraceSession trace
    ) {
        if (onNode == null && !trace.active()) {
            return AgentWorkflowEventListener.NOOP;
        }
        return new AgentWorkflowEventListener() {
            @Override
            public void onNodeStarted(AgentWorkflowNode node, online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext context) {
                String inputSummary = summary(values.input(node, context));
                trace.nodeStarted(node.id(), node.kind(), node.title(), inputSummary);
                if (onNode != null) {
                    onNode.accept(event(node, "RUNNING", "输入：" + inputSummary));
                }
            }

            @Override
            public void onNodeCompleted(
                    AgentWorkflowNode node,
                    AgentWorkflowNodeResult result,
                    online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext context
            ) {
                String outputSummary = summary(result.output());
                trace.nodeCompleted(node.id(), outputSummary);
                if (onNode != null) {
                    onNode.accept(event(node, "COMPLETED", "输出：" + outputSummary));
                }
            }

            @Override
            public void onNodeFailed(
                    AgentWorkflowNode node,
                    RuntimeException error,
                    online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext context
            ) {
                trace.nodeFailed(node.id(), error.getMessage());
                if (onNode != null) {
                    onNode.accept(event(node, "FAILED", error.getMessage() == null ? "节点执行失败" : error.getMessage()));
                }
            }

            @Override
            public void onNodeSkipped(
                    AgentWorkflowNode node,
                    online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext context
            ) {
                trace.nodeSkipped(node.id(), node.kind(), node.title());
                if (onNode != null) {
                    onNode.accept(event(node, "SKIPPED", "条件分支未命中"));
                }
            }
        };
    }

    private AgentDebugEventDTO event(AgentWorkflowNode node, String status, String message) {
        return new AgentDebugEventDTO(node.id(), node.kind(), node.title(), status, message);
    }

    private void ensurePythonRuntime(
            online.yudream.base.application.platform.agent.workflow.AgentWorkflowGraph graph,
            Set<String> systemToolCodes
    ) {
        boolean required = graph.topologicalOrder().stream()
                .anyMatch(node -> requiresPythonRuntime(node, systemToolCodes));
        if (required) {
            capabilityAppService.ensureEnabled("integration", "集成与 Python 运行时");
        }
    }

    private boolean requiresPythonRuntime(AgentWorkflowNode node, Set<String> systemToolCodes) {
        if ("code".equals(node.kind())) {
            return true;
        }
        if ("tool".equals(node.kind())) {
            return isPythonTool(node.data().path("toolCode").asText(), systemToolCodes);
        }
        if (!List.of("llm", "extract", "classify", "vision").contains(node.kind())
                || !modelToolCallingEnabled(node)
                || !node.data().path("toolCodes").isArray()) {
            return false;
        }
        for (com.fasterxml.jackson.databind.JsonNode code : node.data().path("toolCodes")) {
            if (isPythonTool(code.asText(), systemToolCodes)) {
                return true;
            }
        }
        return false;
    }

    private boolean modelToolCallingEnabled(AgentWorkflowNode node) {
        String mode = node.data().path("toolMode").asText("").trim();
        return !"NONE".equalsIgnoreCase(mode);
    }

    private boolean isPythonTool(String toolCode, Set<String> systemToolCodes) {
        if (toolCode == null || toolCode.isBlank() || systemToolCodes.contains(toolCode.trim())) {
            return false;
        }
        return toolRepo.findByCode(toolCode.trim())
                .map(tool -> tool.getType() == online.yudream.base.domain.platform.agent.enumerate.AgentToolType.PYTHON)
                .orElse(false);
    }

    private AgentWorkflowInitialInput initialInput(AgentRunCmd command) {
        List<Map<String, Object>> attachments = command.getAttachments() == null
                ? List.of()
                : command.getAttachments().stream().map(item -> Map.<String, Object>of(
                "name", item.name() == null ? "" : item.name(),
                "fileName", item.name() == null ? "" : item.name(),
                "contentType", item.contentType() == null ? "" : item.contentType(),
                "size", item.size() == null ? 0L : item.size(),
                "dataUrl", item.dataUrl() == null ? "" : item.dataUrl()
        )).toList();
        Map<String, Object> variables = new java.util.LinkedHashMap<>();
        variables.put("attachments", attachments);
        variables.put("attachment", attachments.isEmpty() ? Map.of() : attachments.getFirst());
        variables.put("imageDataUrl", command.getImageDataUrl() == null ? "" : command.getImageDataUrl());
        List<String> imageDataUrls = command.getImageDataUrls() == null || command.getImageDataUrls().isEmpty()
                ? (command.getImageDataUrl() == null || command.getImageDataUrl().isBlank()
                   ? List.of()
                   : List.of(command.getImageDataUrl()))
                : command.getImageDataUrls();
        variables.put("imageDataUrls", imageDataUrls);
        return new AgentWorkflowInitialInput(command.getInput() == null ? "" : command.getInput(), variables);
    }

    private String summary(Object value) {
        String content = content(value).replaceAll("\\s+", " ").trim();
        return content.length() <= 160 ? content : content.substring(0, 157) + "...";
    }

    private String content(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return value.toString();
        }
    }
}
