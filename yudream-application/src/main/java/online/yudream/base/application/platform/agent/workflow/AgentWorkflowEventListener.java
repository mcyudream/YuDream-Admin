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

    default void onNodeSuspended(AgentWorkflowNode node, AgentWorkflowContext context) {
    }

    /** 写入业务检查点，不保存运行对象、权限快照或供应商密钥。 */
    default void onCheckpoint(online.yudream.base.domain.platform.agent.valobj.AgentWorkflowFrame frame) {
    }

    default void onNodeSkipped(AgentWorkflowNode node, AgentWorkflowContext context) {
    }
}
