package online.yudream.base.application.platform.agent.workflow;

public interface AgentWorkflowEventListener {

    AgentWorkflowEventListener NOOP = new AgentWorkflowEventListener() {
    };

    default void onNodeStarted(AgentWorkflowNode node, AgentWorkflowContext context) {
    }

    default void onNodeCompleted(
            AgentWorkflowNode node,
            AgentWorkflowNodeResult result,
            AgentWorkflowContext context
    ) {
    }

    default void onNodeFailed(AgentWorkflowNode node, RuntimeException error, AgentWorkflowContext context) {
    }

    default void onNodeSkipped(AgentWorkflowNode node, AgentWorkflowContext context) {
    }
}
