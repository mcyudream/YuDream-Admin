package online.yudream.base.application.platform.agent.workflow.handler;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNode;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeHandler;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeResult;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;

@RequiredArgsConstructor
public final class AgentConditionNodeHandler implements AgentWorkflowNodeHandler {
    private final AgentWorkflowValueResolver values;

    @Override
    public String kind() {
        return "condition";
    }

    @Override
    public AgentWorkflowNodeResult execute(AgentWorkflowNode node, AgentWorkflowContext context) {
        boolean matched = values.condition(values.text(node, "condition"), context);
        return AgentWorkflowNodeResult.branch(matched, Boolean.toString(matched));
    }
}
