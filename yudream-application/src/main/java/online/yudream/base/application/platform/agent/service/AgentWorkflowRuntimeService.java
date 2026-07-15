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
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowRunState;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.agent.service.AgentPermissionGateway;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.document.service.DocumentTextExtractor;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentWorkflowRuntimeService {
    private final ObjectMapper objectMapper;
    private final RuntimeExecutor runtimeExecutor;
    private final AgentToolRepo toolRepo;
    private final ObjectProvider<AiGenerationGateway> generationGateways;
    private final ObjectProvider<AiAgentTool> systemToolProvider;
    private final AgentKnowledgeOperations knowledgeOperations;
    private final DocumentTextExtractor documentTextExtractor;
    private final AgentPermissionGateway permissionGateway;

    public AgentWorkflowRuntimeResult execute(
            AgentApplication application,
            AgentRunCmd command,
            Map<String, String> aiConfig,
            Consumer<AgentDebugEventDTO> onNode,
            Consumer<String> onDelta,
            Consumer<AiAgentToolResult> onTool
    ) {
        List<AiAgentTool> systemTools = systemToolProvider.stream().toList();
        List<AiAgentTool> selectedTools = systemTools.stream()
                .map(tool -> tool.descriptor().name())
                .filter(code -> application.getToolCodes() != null && application.getToolCodes().contains(code))
                .map(code -> systemTools.stream().filter(tool -> code.equals(tool.descriptor().name())).findFirst().orElseThrow())
                .toList();
        selectedTools.forEach(tool -> ensureToolPermission(tool.descriptor().permissionCode(), tool.descriptor().title()));
        Set<String> selectedSystemTools = selectedTools.stream()
                .map(tool -> tool.descriptor().name())
                .collect(Collectors.toSet());
        AgentWorkflowRunState state = new AgentWorkflowRunState(
                application,
                command,
                aiConfig,
                selectedSystemTools,
                onDelta,
                onTool
        );
        AgentWorkflowValueResolver values = new AgentWorkflowValueResolver(objectMapper);
        AgentWorkflowGraphParser graphParser = new AgentWorkflowGraphParser(objectMapper);
        var graph = graphParser.parse(application.getWorkflowJson());
        AgentWorkflowExecutor executor = new AgentWorkflowExecutor(graphParser, handlers(values, application, state, systemTools));
        AgentWorkflowExecution execution = executor.execute(
                application.getWorkflowJson(),
                initialInput(command),
                listener(onNode, values)
        );
        Object output = graph.topologicalOrder().stream()
                .filter(node -> "end".equals(node.kind()))
                .filter(node -> execution.executedNodeIds().contains(node.id()))
                .map(node -> execution.context().nodeOutput(node.id()))
                .reduce((left, right) -> right)
                .orElseThrow(() -> new online.yudream.base.domain.common.exception.BizException("工作流未执行结束节点"));
        return new AgentWorkflowRuntimeResult(content(output), state.toolResults());
    }

    private List<AgentWorkflowNodeHandler> handlers(
            AgentWorkflowValueResolver values,
            AgentApplication application,
            AgentWorkflowRunState state,
            List<AiAgentTool> systemTools
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
                values, objectMapper, runtimeExecutor, toolRepo, systemTools, application, state, permissionGateway
        ));
        for (String kind : List.of("search", "vector", "rerank", "embedding")) {
            handlers.add(new AgentKnowledgeNodeHandler(kind, values, objectMapper, knowledgeOperations));
        }
        AiGenerationGateway generationGateway = generationGateways.getIfAvailable();
        if (generationGateway != null) {
            handlers.add(new AgentLlmNodeHandler("understand", values, objectMapper, generationGateway, state));
            handlers.add(new AgentLlmNodeHandler("llm", values, objectMapper, generationGateway, state));
        }
        return handlers;
    }

    private AgentWorkflowEventListener listener(
            Consumer<AgentDebugEventDTO> onNode,
            AgentWorkflowValueResolver values
    ) {
        if (onNode == null) {
            return AgentWorkflowEventListener.NOOP;
        }
        return new AgentWorkflowEventListener() {
            @Override
            public void onNodeStarted(AgentWorkflowNode node, online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext context) {
                onNode.accept(event(node, "RUNNING", "输入：" + summary(values.input(node, context))));
            }

            @Override
            public void onNodeCompleted(
                    AgentWorkflowNode node,
                    AgentWorkflowNodeResult result,
                    online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext context
            ) {
                onNode.accept(event(node, "COMPLETED", "输出：" + summary(result.output())));
            }

            @Override
            public void onNodeFailed(
                    AgentWorkflowNode node,
                    RuntimeException error,
                    online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext context
            ) {
                onNode.accept(event(node, "FAILED", error.getMessage() == null ? "节点执行失败" : error.getMessage()));
            }

            @Override
            public void onNodeSkipped(
                    AgentWorkflowNode node,
                    online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext context
            ) {
                onNode.accept(event(node, "SKIPPED", "条件分支未命中"));
            }
        };
    }

    private AgentDebugEventDTO event(AgentWorkflowNode node, String status, String message) {
        return new AgentDebugEventDTO(node.id(), node.kind(), node.title(), status, message);
    }

    private void ensureToolPermission(String permissionCode, String toolName) {
        if (!permissionGateway.hasPermission(permissionCode)) {
            throw new online.yudream.base.domain.common.exception.BizException("无权限调用工具：" + toolName);
        }
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
