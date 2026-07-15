package online.yudream.base.infra.system.security.service;

import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.service.UserContextStore;
import online.yudream.base.domain.system.user.valobj.PermissionID;
import online.yudream.base.domain.system.user.valobj.RoleID;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SaTokenPermissionProvider implements StpInterface {

    private static final String ALL_PERMISSION = "*";

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final UserContextStore userContextStore;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        User user = getUser(loginId);
        if (user == null || user.getRoles() == null || user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            return List.of();
        }
        Role role = currentRole(user, Long.parseLong(String.valueOf(loginId)));
        if (role == null || role.getStatus() != RoleStatus.ACTIVE) {
            return List.of();
        }
        if (role.getSystemType() == SystemRoleType.SUPER_ADMIN) {
            return List.of(ALL_PERMISSION);
        }
        if (role.getPermissions() == null) {
            return List.of();
        }
        return role.getPermissions().stream().map(PermissionID::getCode).toList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = getUser(loginId);
        if (user == null || user.getRoles() == null || user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            return List.of();
        }
        Role role = currentRole(user, Long.parseLong(String.valueOf(loginId)));
        return role != null && role.getStatus() == RoleStatus.ACTIVE ? List.of(role.getCode()) : List.of();
    }

    private User getUser(Object loginId) {
        if (loginId == null) {
            return null;
        }
        return userRepo.findById(Long.parseLong(String.valueOf(loginId))).orElse(null);
    }

    private Role currentRole(User user, Long userId) {
        List<RoleID> roles = user.getRoles();
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        Long currentRoleId = userContextStore.getCurrentRoleId(userId);
        RoleID selectedRoleId = currentRoleId == null
                ? roles.getFirst()
                : roles.stream().filter(roleId -> roleId.getValue().equals(currentRoleId)).findFirst().orElse(null);
        return selectedRoleId == null ? null : roleRepo.findById(selectedRoleId.getValue()).orElse(null);
    }
}
