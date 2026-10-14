package online.yudream.base.application.platform.agent.workflow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AgentWorkflowContext {

    private final Object input;
    private final Map<String, Object> nodeOutputs = new LinkedHashMap<>();
    private final Map<String, Object> variables = new LinkedHashMap<>();

    AgentWorkflowContext(Object input) {
        if (input instanceof AgentWorkflowInitialInput initial) {
            this.input = initial.input();
            variables.putAll(initial.variables());
        }
        else {
            this.input = input;
        }
        variables.put("input", this.input);
    }

    static AgentWorkflowContext restore(online.yudream.base.domain.platform.agent.valobj.AgentWorkflowFrame frame) {
        AgentWorkflowContext context = new AgentWorkflowContext(frame.input());
        context.nodeOutputs.putAll(frame.nodeOutputs());
        context.variables.clear();
        context.variables.putAll(frame.variables());
        return context;
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
