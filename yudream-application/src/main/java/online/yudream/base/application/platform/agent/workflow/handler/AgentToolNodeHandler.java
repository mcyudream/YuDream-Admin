package online.yudream.base.application.platform.agent.workflow.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNode;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeHandler;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeResult;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowRunState;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.application.platform.agent.workflow.support.AgentPythonToolContract;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentTool;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolType;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.agent.service.AgentPermissionGateway;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import online.yudream.base.domain.platform.integration.valobj.RuntimeExecutionResult;

import java.util.List;
import java.util.Map;

public final class AgentToolNodeHandler implements AgentWorkflowNodeHandler {
    private static final TypeReference<Map<String, Object>> ARGUMENTS = new TypeReference<>() {};

    private final AgentWorkflowValueResolver values;
    private final ObjectMapper objectMapper;
    private final RuntimeExecutor runtimeExecutor;
    private final AgentToolRepo toolRepo;
    private final List<AiAgentTool> systemTools;
    private final AgentApplication application;
    private final AgentWorkflowRunState state;
    private final AgentPermissionGateway permissionGateway;

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
        this.values = values;
        this.objectMapper = objectMapper;
        this.runtimeExecutor = runtimeExecutor;
        this.toolRepo = toolRepo;
        this.systemTools = systemTools == null ? List.of() : List.copyOf(systemTools);
        this.application = application;
        this.state = state;
        this.permissionGateway = permissionGateway;
    }

    @Override
    public String kind() {
        return "tool";
    }

    @Override
    public AgentWorkflowNodeResult execute(AgentWorkflowNode node, AgentWorkflowContext context) {
        String toolCode = values.text(node, "toolCode");
        if (toolCode.isBlank()) {
            throw new BizException("工具节点必须选择工具");
        }
        if (application.getToolCodes() == null || !application.getToolCodes().contains(toolCode)) {
            throw new BizException("应用未授权工具：" + toolCode);
        }
        Map<String, Object> arguments = arguments(values.input(node, context));
        AiAgentToolResult result = toolRepo.findByCode(toolCode)
                .map(tool -> executePython(tool, arguments))
                .orElseGet(() -> executeSystem(toolCode, arguments));
        state.addToolResult(result);
        Object output = result.payload() == null || result.payload().isEmpty() ? result.message() : result.payload();
        return values.result(node, output);
    }

    private AiAgentToolResult executeSystem(String toolCode, Map<String, Object> arguments) {
        AiAgentTool tool = systemTools.stream()
                .filter(item -> toolCode.equals(item.descriptor().name()))
                .findFirst()
                .orElseThrow(() -> new BizException("系统工具不存在：" + toolCode));
        ensurePermission(tool.descriptor().permissionCode(), tool.descriptor().title());
        try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(java.util.Set.of(toolCode))) {
            return tool.execute(new AiAgentToolCall(toolCode, arguments));
        }
    }

    private AiAgentToolResult executePython(AgentTool tool, Map<String, Object> arguments) {
        if (tool.getType() != AgentToolType.PYTHON || !Boolean.TRUE.equals(tool.getEnabled())) {
            throw new BizException("Python 工具不可用：" + tool.getCode());
        }
        ensurePermission(tool.getPermissionCode(), tool.getName());
        String executableCode = AgentPythonToolContract.wrap(tool.getPythonCode());
        RuntimeScript script = RuntimeScript.create(tool.getName(), tool.getCode(), RuntimeLanguage.PYTHON, executableCode);
        script.update(
                tool.getName(),
                RuntimeLanguage.PYTHON,
                executableCode,
                tool.getTimeoutMillis(),
                Map.of(),
                ConnectorStatus.ACTIVE
        );
        RuntimeExecutionResult execution;
        try {
            execution = runtimeExecutor.execute(script, objectMapper.writeValueAsString(arguments));
        } catch (Exception exception) {
            throw new BizException("无法序列化 Python 工具参数");
        }
        if (execution.status() != ExecutionStatus.SUCCESS) {
            throw new BizException("Python 工具执行失败：" + (execution.errorMessage() == null ? execution.stderr() : execution.errorMessage()));
        }
        Map<String, Object> output;
        try {
            output = objectMapper.readValue(execution.stdout(), ARGUMENTS);
        } catch (Exception exception) {
            throw new BizException("Python 工具 run() 必须返回可序列化的字典");
        }
        return new AiAgentToolResult(
                tool.getCode(),
                "python",
                tool.getPermissionCode(),
                "执行完成",
                output
        );
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

    private void ensurePermission(String permissionCode, String toolName) {
        if (!permissionGateway.hasPermission(permissionCode)) {
            throw new BizException("无权限调用工具：" + toolName);
        }
    }
}
