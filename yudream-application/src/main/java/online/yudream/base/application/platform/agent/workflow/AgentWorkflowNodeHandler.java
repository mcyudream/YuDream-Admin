package online.yudream.base.application.platform.agent.workflow;

public interface AgentWorkflowNodeHandler {

    String kind();

    AgentWorkflowNodeResult execute(AgentWorkflowNode node, AgentWorkflowContext context);
}
