package online.yudream.base.domain.platform.agent.service;

public interface AgentPermissionGateway {
    boolean hasPermission(String permissionCode);
}
