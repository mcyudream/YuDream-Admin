package online.yudream.base.interfaces.platform.agent.support;

import online.yudream.base.domain.system.security.anno.PermissionRegister;
import org.springframework.stereotype.Component;

/**
 * Agent 工作流 Python 代码节点门禁：编写/修改代码节点是应用进程内执行任意代码的入口，
 * 要求高于普通「编辑应用」的专门权限；注解同时用于启动期权限码注册。
 */
@Component
public class AgentCodeNodeGate {

    @PermissionRegister(code = "platform:agent:code:manage", name = "管理 Agent 代码节点", module = "平台能力", desc = "在 Agent 工作流中编写与修改 Python 代码节点")
    public void ensureMayManageCodeNode() {
        // 权限校验由 @PermissionRegister 切面执行：无权限时抛出未授权
    }
}
