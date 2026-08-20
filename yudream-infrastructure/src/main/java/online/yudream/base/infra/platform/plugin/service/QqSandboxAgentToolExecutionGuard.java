package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.agent.service.AgentToolExecutionGuard;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class QqSandboxAgentToolExecutionGuard implements AgentToolExecutionGuard {
    @Override
    public void check(String toolName, AgentToolRisk risk, String permissionCode) {
        QqSandboxSession session = QqSandboxExecutionScope.requireActive();
        if (session == null) return;
        AgentToolRisk effectiveRisk = risk == null ? AgentToolRisk.WRITE : risk;
        if (effectiveRisk != AgentToolRisk.READ) {
            session.append("guard", "tool.rejected", session.pluginCode(), Map.of(
                    "tool", safe(toolName), "risk", effectiveRisk.name(), "permission", safe(permissionCode)));
            throw new BizException("QQ 沙箱仅允许调用只读工具：" + safe(toolName));
        }
        session.append("guard", "tool.allowed", session.pluginCode(), Map.of(
                "tool", safe(toolName), "risk", effectiveRisk.name(), "permission", safe(permissionCode)));
    }

    private String safe(String value) { return value == null ? "" : value; }
}
