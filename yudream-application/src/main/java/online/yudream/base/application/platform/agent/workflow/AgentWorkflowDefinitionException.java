package online.yudream.base.application.platform.agent.workflow;

import online.yudream.base.domain.common.exception.BizException;

public class AgentWorkflowDefinitionException extends BizException {

    public AgentWorkflowDefinitionException(String message) {
        super(message);
    }

    public AgentWorkflowDefinitionException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
