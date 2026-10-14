package online.yudream.base.application.platform.agent.workflow;

import online.yudream.base.domain.platform.ai.valobj.HarnessRunResult;

/** 原生交互暂停是业务图等待条件，不是节点失败或可供后继节点消费的输出。 */
public final class AgentWorkflowSuspendedException extends RuntimeException {
    private final HarnessRunResult nativeResult;
    public AgentWorkflowSuspendedException(HarnessRunResult result) {
        super("工作流正在等待原生工具交互");
        this.nativeResult = result;
    }
    public HarnessRunResult nativeResult() { return nativeResult; }
}
