package online.yudream.base.application.platform.agent.workflow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AgentWorkflowContext {

    private final Object input;
    private final Map<String, Object> nodeOutputs = new LinkedHashMap<>();
    private final Map<String, Object> variables = new LinkedHashMap<>();

    AgentWorkflowContext(Object input) {
        this.input = input;
        variables.put("input", input);
    }

    public Object input() {
        return input;
    }

    public Object nodeOutput(String nodeId) {
        return nodeOutputs.get(nodeId);
    }

    public Object variable(String name) {
        return variables.get(name);
    }

    public Map<String, Object> nodeOutputs() {
        return Collections.unmodifiableMap(nodeOutputs);
    }

    public Map<String, Object> variables() {
        return Collections.unmodifiableMap(variables);
    }

    void record(String nodeId, AgentWorkflowNodeResult result) {
        nodeOutputs.put(nodeId, result.output());
        variables.putAll(result.variables());
    }
}
