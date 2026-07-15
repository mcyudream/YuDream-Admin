package online.yudream.base.application.platform.agent.workflow;

import java.util.Map;

public record AgentWorkflowInitialInput(Object input, Map<String, Object> variables) {
    public AgentWorkflowInitialInput {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
