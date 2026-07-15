package online.yudream.base.application.platform.agent.workflow.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowContext;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNode;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeHandler;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowNodeResult;
import online.yudream.base.application.platform.agent.workflow.support.AgentWorkflowValueResolver;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import online.yudream.base.domain.platform.integration.valobj.RuntimeExecutionResult;

import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
public final class AgentCodeNodeHandler implements AgentWorkflowNodeHandler {
    private final AgentWorkflowValueResolver values;
    private final ObjectMapper objectMapper;
    private final RuntimeExecutor runtimeExecutor;

    @Override
    public String kind() {
        return "code";
    }

    @Override
    public AgentWorkflowNodeResult execute(AgentWorkflowNode node, AgentWorkflowContext context) {
        String code = values.text(node, "code");
        if (code.isBlank()) {
            throw new BizException("代码节点必须配置 Python 脚本");
        }
        int timeout = Math.clamp(values.integer(node, "timeoutMillis", 30_000), 100, 120_000);
        RuntimeScript script = RuntimeScript.create(node.title(), "agent-node-" + node.id(), RuntimeLanguage.PYTHON, code);
        script.update(node.title(), RuntimeLanguage.PYTHON, code, timeout, Map.of(), ConnectorStatus.ACTIVE);
        RuntimeExecutionResult execution = runtimeExecutor.execute(script, stdin(node, context));
        if (execution.status() != ExecutionStatus.SUCCESS) {
            String detail = execution.errorMessage() == null || execution.errorMessage().isBlank()
                    ? execution.stderr()
                    : execution.errorMessage();
            throw new BizException("Python 节点执行失败：" + (detail == null ? execution.status().name() : detail));
        }
        return values.result(node, parseOutput(execution.stdout()));
    }

    private String stdin(AgentWorkflowNode node, AgentWorkflowContext context) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("input", values.input(node, context));
            payload.put("variables", context.variables());
            payload.put("nodes", context.nodeOutputs());
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new BizException("无法序列化 Python 节点输入");
        }
    }

    private Object parseOutput(String stdout) {
        String output = stdout == null ? "" : stdout.trim();
        if (output.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.readValue(output, Object.class);
        } catch (Exception ignored) {
            return output;
        }
    }
}
