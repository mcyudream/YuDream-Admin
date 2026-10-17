package online.yudream.base.infra.platform.wiki.service;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.wiki.service.WikiActorGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiActor;
import online.yudream.base.domain.system.security.service.ApiKeyAuthenticationContext;
import online.yudream.base.domain.system.security.valobj.ApiKeyAuthentication;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.service.UserContextStore;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.RoleID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Wiki 认证主体解析：身份只取 Sa-Token 登录态或 API Key 上下文，按当前生效角色（部门/角色上下文）解析。
 * <p>
 * 与 {@code SaTokenPermissionProvider} 保持同一口径：用户必须启用且邮箱已验证、角色必须启用；
 * 任一环节缺失按未认证处理，由 Wiki 用例 fail closed。
 */
@Component
@RequiredArgsConstructor
public class SaTokenWikiActorGateway implements WikiActorGateway {

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final UserContextStore userContextStore;

    @Override
    public WikiActor currentActor() {
        return actorFor(currentUserId());
    }

    /**
     * 指定用户的主体快照：与 {@link #currentActor()} 同一解析口径，只是身份来自调用方给出的用户 ID。
     * AI 运行线程没有 Sa-Token 请求上下文，工具侧只能这样取主体；角色/部门仍按该用户持久化的当前上下文解析。
     */
    @Override
    public WikiActor actorFor(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userRepo.findById(userId).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            return null;
        }
        Role role = currentRole(user, userId);
        if (role == null || role.getStatus() != RoleStatus.ACTIVE) {
            return null;
        }
        return WikiActor.of(userId, role.getId(), displayName(user), role.getSystemType() == SystemRoleType.SUPER_ADMIN);
    }

    @Override
    public boolean hasPermission(String permissionCode) {
        if (!StringUtils.hasText(permissionCode)) {
            return false;
        }
        ApiKeyAuthentication apiKey = ApiKeyAuthenticationContext.get();
        if (apiKey != null) {
            return apiKey.hasPermission(permissionCode);
        }
        return StpUtil.getLoginIdDefaultNull() != null && StpUtil.hasPermission(permissionCode);
    }

    private Long currentUserId() {
        ApiKeyAuthentication apiKey = ApiKeyAuthenticationContext.get();
        if (apiKey != null) {
            return apiKey.userId();
        }
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(loginId));
        }
        catch (NumberFormatException error) {
            return null;
        }
    }

    private Role currentRole(User user, Long userId) {
        List<RoleID> roleIds = user.getRoles();
        if (roleIds == null || roleIds.isEmpty()) {
            return null;
        }
        List<Role> ownedRoles = roleRepo.findByIds(roleIds.stream().map(RoleID::getValue).distinct().toList());
        DeptID deptId = user.resolveContextDeptID(userContextStore.getCurrentDeptId(userId));
        RoleID roleId = user.resolveContextRole(deptId, userContextStore.getCurrentRoleId(userId), ownedRoles);
        if (roleId == null) {
            return null;
        }
        return ownedRoles.stream().filter(role -> role.getId().equals(roleId.getValue())).findFirst().orElse(null);
    }

    private String displayName(User user) {
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        return user.getUsername() == null ? "" : user.getUsername().trim();
    }
}
