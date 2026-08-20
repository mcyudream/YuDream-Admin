package online.yudream.base.domain.platform.agent.service;

import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;

@FunctionalInterface
public interface AgentToolExecutionGuard {
    AgentToolExecutionGuard ALLOW_ALL = (toolName, risk, permissionCode) -> { };

    void check(String toolName, AgentToolRisk risk, String permissionCode);
}
