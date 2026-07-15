package online.yudream.base.application.platform.agent.workflow;

import java.util.List;

public record AgentWorkflowExecution(AgentWorkflowContext context, List<String> executedNodeIds) {

    public AgentWorkflowExecution {
        executedNodeIds = List.copyOf(executedNodeIds);
    }
}
