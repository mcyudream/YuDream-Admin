package online.yudream.base.application.platform.agent.workflow.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNode;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeHandler;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeResult;
import online.yudream.base.application.platform.agent.workflow.support.AgentToolExecutor;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowRunState;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.agent.service.AgentPermissionGateway;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;

import java.util.List;
import java.util.Map;

/** Compatibility handler for existing workflow tool nodes. */
public final class AgentToolNodeHandler implements AgentWorkflowNodeHandler {
    private static final TypeReference<Map<String, Object>> ARGUMENTS = new TypeReference<>() { };

    private final AgentWorkflowValueResolver values;
    private final ObjectMapper objectMapper;
    private final AgentToolExecutor toolExecutor;
    private final AgentApplication application;
    private final AgentWorkflowRunState state;

    public AgentToolNodeHandler(
            AgentWorkflowValueResolver values,
            ObjectMapper objectMapper,
            AgentToolExecutor toolExecutor,
            AgentApplication application,
            AgentWorkflowRunState state
    ) {
        this.values = values;
        this.objectMapper = objectMapper;
        this.toolExecutor = toolExecutor;
        this.application = application;
        this.state = state;
    }

    public AgentToolNodeHandler(
            AgentWorkflowValueResolver values,
            ObjectMapper objectMapper,
            RuntimeExecutor runtimeExecutor,
            AgentToolRepo toolRepo,
            List<AiAgentTool> systemTools,
            AgentApplication application,
            AgentWorkflowRunState state,
            AgentPermissionGateway permissionGateway
    ) {
        this(
                values,
                objectMapper,
                new AgentToolExecutor(objectMapper, runtimeExecutor, toolRepo, systemTools, permissionGateway),
                application,
                state
        );
    }

    @Override
    public String kind() {
        return "tool";
    }

    @Override
    public AgentWorkflowNodeResult execute(AgentWorkflowNode node, AgentWorkflowContext context) {
        String toolCode = values.text(node, "toolCode");
        if (toolCode.isBlank()) {
            throw new BizException("Tool node must select a tool");
        }
        AiAgentToolResult result = toolExecutor.execute(
                toolCode,
                arguments(values.input(node, context)),
                application,
                state.command()
        );
        state.addToolResult(result);
        Object output = result.payload() == null || result.payload().isEmpty() ? result.message() : result.payload();
        return values.result(node, output);
    }

    private Map<String, Object> arguments(Object input) {
        if (input instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, ARGUMENTS);
        }
        if (input instanceof String value) {
            try {
                return objectMapper.readValue(value, ARGUMENTS);
            } catch (Exception ignored) {
                return Map.of("input", value);
            }
        }
        return Map.of("input", input == null ? "" : input);
    }
}
