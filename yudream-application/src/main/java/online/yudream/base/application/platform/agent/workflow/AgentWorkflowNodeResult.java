package online.yudream.base.application.platform.agent.workflow;

import java.util.LinkedHashMap;
import java.util.Map;

public record AgentWorkflowNodeResult(Object output, Map<String, Object> variables, String selectedSourceHandle) {

    public AgentWorkflowNodeResult {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public static AgentWorkflowNodeResult output(Object output) {
        return new AgentWorkflowNodeResult(output, Map.of(), null);
    }

    public static AgentWorkflowNodeResult branch(Object output, String selectedSourceHandle) {
        if (selectedSourceHandle == null || selectedSourceHandle.isBlank()) {
            throw new IllegalArgumentException("分支 sourceHandle 不能为空");
        }
        return new AgentWorkflowNodeResult(output, Map.of(), selectedSourceHandle);
    }

    public AgentWorkflowNodeResult withVariables(Map<String, ?> additionalVariables) {
        Map<String, Object> merged = new LinkedHashMap<>(variables);
        if (additionalVariables != null) {
            merged.putAll(additionalVariables);
        }
        return new AgentWorkflowNodeResult(output, merged, selectedSourceHandle);
    }
}
