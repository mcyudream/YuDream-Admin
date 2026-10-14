package online.yudream.base.application.platform.agent.workflow;

import java.util.List;

public record AgentWorkflowExecution(AgentWorkflowContext context, List<String> executedNodeIds,
                                      online.yudream.base.domain.platform.agent.valobj.AgentWorkflowFrame checkpoint) {

    public AgentWorkflowExecution {
        executedNodeIds = List.copyOf(executedNodeIds);
    }
    public AgentWorkflowExecution(AgentWorkflowContext context, List<String> executedNodeIds) {
        this(context, executedNodeIds, null);
    }
    public boolean waiting() { return checkpoint != null; }
}
