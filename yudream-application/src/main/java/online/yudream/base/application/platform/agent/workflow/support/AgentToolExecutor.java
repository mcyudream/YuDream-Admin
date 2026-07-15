package online.yudream.base.application.platform.agent.workflow.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentTool;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolType;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.agent.service.AgentPermissionGateway;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import online.yudream.base.domain.platform.integration.valobj.RuntimeExecutionResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves and executes Agent tools with the caller's permission snapshot. */
public final class AgentToolExecutor {
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final RuntimeExecutor runtimeExecutor;
    private final AgentToolRepo toolRepo;
    private final List<AiAgentTool> systemTools;
    private final AgentPermissionGateway permissionGateway;

    public AgentToolExecutor(
            ObjectMapper objectMapper,
            RuntimeExecutor runtimeExecutor,
            AgentToolRepo toolRepo,
            List<AiAgentTool> systemTools,
            AgentPermissionGateway permissionGateway
    ) {
        this.objectMapper = objectMapper;
        this.runtimeExecutor = runtimeExecutor;
        this.toolRepo = toolRepo;
        this.systemTools = systemTools == null ? List.of() : List.copyOf(systemTools);
        this.permissionGateway = permissionGateway;
    }

    /** Authorization happens before a model can receive a callback descriptor. */
    public AiAgentTool resolve(String toolCode, AgentApplication application, AgentRunCmd command) {
        return resolve(toolCode, application, capturePermissionSnapshot(command));
    }

    public PermissionSnapshot capturePermissionSnapshot(AgentRunCmd command) {
        List<String> permissionCodes = command == null || command.getPermissionCodes() == null
                ? List.of()
                : command.getPermissionCodes().stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .toList();
        return new PermissionSnapshot(permissionCodes, command != null && command.isPermissionContextExplicit());
    }

    AiAgentTool resolve(String toolCode, AgentApplication application, PermissionSnapshot permissionSnapshot) {
        String code = requiredCode(toolCode);
        ensureApplicationGrant(code, application);
        AiAgentTool systemTool = systemTools.stream()
                .filter(tool -> code.equals(tool.descriptor().name()))
                .findFirst()
                .orElse(null);
        if (systemTool != null) {
            ensurePermission(systemTool.descriptor().permissionCode(), systemTool.descriptor().title(), permissionSnapshot);
            return systemTool;
        }
        AgentTool pythonTool = toolRepo.findByCode(code)
                .orElseThrow(() -> new BizException("Tool does not exist: " + code));
        ensurePythonAvailable(pythonTool);
        ensurePermission(pythonTool.getPermissionCode(), pythonTool.getName(), permissionSnapshot);
        return new PythonToolAdapter(pythonTool, pythonDescriptor(pythonTool), permissionSnapshot);
    }

    /** Makes old tool nodes use the exact same resolution and Python execution contract. */
    public AiAgentToolResult execute(
            String toolCode,
            Map<String, Object> arguments,
            AgentApplication application,
            AgentRunCmd command
    ) {
        PermissionSnapshot permissionSnapshot = capturePermissionSnapshot(command);
        AiAgentTool tool = resolve(toolCode, application, permissionSnapshot);
        Map<String, Object> safeArguments = arguments == null ? Map.of() : new LinkedHashMap<>(arguments);
        try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(List.of(tool))) {
            ensurePermission(tool.descriptor().permissionCode(), tool.descriptor().title(), permissionSnapshot);
            return tool.execute(new AiAgentToolCall(tool.descriptor().name(), safeArguments));
        }
    }

    private AiAgentToolDescriptor pythonDescriptor(AgentTool tool) {
        String description = tool.getDescription() == null ? "" : tool.getDescription().trim();
        String permissionCode = tool.getPermissionCode() == null ? "" : tool.getPermissionCode().trim();
        return new AiAgentToolDescriptor(
                tool.getCode(),
                tool.getName(),
                description,
                permissionCode,
                permissionCode,
                "Agent custom tool",
                description,
                Map.of(),
                parseSchema(tool)
        );
    }

    private Map<String, Object> parseSchema(AgentTool tool) {
        try {
            Map<String, Object> schema = objectMapper.readValue(tool.getInputSchemaJson(), JSON_OBJECT);
            if (schema == null) {
                throw invalidSchema(tool);
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(schema));
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidSchema(tool);
        }
    }

    private BizException invalidSchema(AgentTool tool) {
        return new BizException("Python tool input schema must be a JSON object: " + tool.getCode());
    }

    private AiAgentToolResult executePython(AgentTool tool, Map<String, Object> arguments) {
        String executableCode = AgentPythonToolContract.wrap(tool.getPythonCode());
        RuntimeScript script = RuntimeScript.create(tool.getName(), tool.getCode(), RuntimeLanguage.PYTHON, executableCode);
        script.update(tool.getName(), RuntimeLanguage.PYTHON, executableCode, tool.getTimeoutMillis(), Map.of(), ConnectorStatus.ACTIVE);
        String serializedArguments;
        try {
            serializedArguments = objectMapper.writeValueAsString(arguments);
        } catch (Exception exception) {
            throw new BizException("Unable to serialize Python tool arguments");
        }
        RuntimeExecutionResult execution = runtimeExecutor.execute(script, serializedArguments);
        if (execution.status() != ExecutionStatus.SUCCESS) {
            String message = execution.errorMessage() == null ? execution.stderr() : execution.errorMessage();
            throw new BizException("Python tool execution failed: " + (message == null ? "unknown error" : message));
        }
        Map<String, Object> output;
        try {
            output = objectMapper.readValue(execution.stdout(), JSON_OBJECT);
        } catch (Exception exception) {
            throw new BizException("Python tool run() must return a serializable dict");
        }
        if (output == null) {
            throw new BizException("Python tool run() must return a serializable dict");
        }
        return new AiAgentToolResult(tool.getCode(), "python", tool.getPermissionCode(), "completed", output);
    }

    private void ensureApplicationGrant(String toolCode, AgentApplication application) {
        if (application == null || application.getToolCodes() == null || !application.getToolCodes().contains(toolCode)) {
            throw new BizException("Application has not granted tool: " + toolCode);
        }
    }

    private void ensurePythonAvailable(AgentTool tool) {
        if (tool.getType() != AgentToolType.PYTHON || !Boolean.TRUE.equals(tool.getEnabled())) {
            throw new BizException("Python tool is unavailable: " + tool.getCode());
        }
    }

    private void ensurePermission(String permissionCode, String toolName, PermissionSnapshot permissionSnapshot) {
        if (!permissionGateway.hasPermission(
                permissionCode,
                permissionSnapshot.permissionCodes(),
                permissionSnapshot.permissionContextExplicit()
        )) {
            throw new BizException("No permission to invoke tool: " + toolName);
        }
    }

    private String requiredCode(String toolCode) {
        if (toolCode == null || toolCode.isBlank()) {
            throw new BizException("Tool code is required");
        }
        return toolCode.trim();
    }

    private final class PythonToolAdapter implements AiAgentTool {
        private final AgentTool tool;
        private final AiAgentToolDescriptor descriptor;
        private final PermissionSnapshot permissionSnapshot;

        private PythonToolAdapter(AgentTool tool, AiAgentToolDescriptor descriptor, PermissionSnapshot permissionSnapshot) {
            this.tool = tool;
            this.descriptor = descriptor;
            this.permissionSnapshot = permissionSnapshot;
        }

        @Override
        public AiAgentToolDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public AiAgentToolResult execute(AiAgentToolCall call) {
            ensurePythonAvailable(tool);
            ensurePermission(tool.getPermissionCode(), tool.getName(), permissionSnapshot);
            Map<String, Object> arguments = call == null || call.arguments() == null
                    ? Map.of()
                    : new LinkedHashMap<>(call.arguments());
            return executePython(tool, arguments);
        }
    }

    public record PermissionSnapshot(List<String> permissionCodes, boolean permissionContextExplicit) {
        public PermissionSnapshot {
            permissionCodes = permissionCodes == null ? List.of() : List.copyOf(permissionCodes);
        }
    }
}
