package online.yudream.base.infra.platform.ai.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.ai.service.AiActorPermissionGateway;
import online.yudream.base.infra.system.security.service.SaTokenPermissionProvider;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SaTokenAiActorPermissionGateway implements AiActorPermissionGateway {
    private final SaTokenPermissionProvider permissions;
    public List<String> currentPermissions(Long userId) {
        // PermissionProvider 自身保证他人的 TokenSession 不会被使用，跨用户读取回默认部门/角色。
        return userId == null || userId == 0L ? List.of() : permissions.getPermissionList(userId, "login");
    }
}
