package online.yudream.base.domain.platform.agent.valobj;

import java.util.List;

/**
 * 一次 Agent 调用的权限快照：由应用层在调用入口从服务端解析后放入 RuntimeContext，
 * 工具执行时按快照复核，禁止依赖前端传入或线程上下文（Reactor 线程无 Sa-Token 上下文）。
 */
public record AgentToolPermissions(List<String> permissionCodes, boolean explicitContext) {

    public static AgentToolPermissions explicit(List<String> permissionCodes) {
        return new AgentToolPermissions(permissionCodes == null ? List.of() : List.copyOf(permissionCodes), true);
    }

    public static AgentToolPermissions absent() {
        return new AgentToolPermissions(List.of(), false);
    }

    public List<String> codes() {
        return permissionCodes == null ? List.of() : permissionCodes;
    }
}
