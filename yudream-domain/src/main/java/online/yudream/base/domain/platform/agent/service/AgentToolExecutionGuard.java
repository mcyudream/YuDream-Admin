package online.yudream.base.domain.platform.agent.service;

import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;

@FunctionalInterface
public interface AgentToolExecutionGuard {
    AgentToolExecutionGuard ALLOW_ALL = (toolName, risk, permissionCode) -> { };

    void check(String toolName, AgentToolRisk risk, String permissionCode);

    /** 在进入模型后台线程前捕获可信运行上下文。 */
    default AgentToolExecutionGuard capture() { return this; }

    /** 守卫上下文覆盖整个真实工具动作，而不只覆盖一个预检查。 */
    default <T> T execute(String toolName, AgentToolRisk risk, String permissionCode, java.util.function.Supplier<T> action) {
        check(toolName, risk, permissionCode);
        return action.get();
    }
}
