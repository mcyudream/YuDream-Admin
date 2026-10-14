package online.yudream.base.domain.platform.agent.valobj;

import online.yudream.base.domain.platform.ai.valobj.HarnessRunResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 宿主业务图检查点；AgentScope 仍拥有模型会话、工具暂停和执行日志。 */
public record AgentWorkflowFrame(int cursor, String pausedNodeId, Object input,
                                 List<String> activatedNodeIds, List<String> executedNodeIds,
                                 Map<String, Object> nodeOutputs, Map<String, Object> variables,
                                 HarnessRunResult suspendedNative) {
    public AgentWorkflowFrame {
        activatedNodeIds = List.copyOf(activatedNodeIds);
        executedNodeIds = List.copyOf(executedNodeIds);
        nodeOutputs = Collections.unmodifiableMap(new LinkedHashMap<>(nodeOutputs));
        variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }
}
