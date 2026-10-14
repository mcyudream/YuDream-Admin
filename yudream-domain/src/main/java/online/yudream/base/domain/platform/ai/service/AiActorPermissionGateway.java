package online.yudream.base.domain.platform.ai.service;

import java.util.List;

/** 管理审批只批准交互，工具权限仍取原调用用户的实时角色。 */
public interface AiActorPermissionGateway {
    List<String> currentPermissions(Long userId);
}
