package online.yudream.base.infra.system.security.service;

import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
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

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        User user = getUser(loginId);
        if (user == null || user.getRoles() == null) {
            return List.of();
        }
        Set<String> permissions = new HashSet<>();
        for (RoleID roleId : user.getRoles()) {
            Role role = roleRepo.findById(roleId.getValue()).orElse(null);
            if (role == null || role.getStatus() != RoleStatus.ACTIVE) {
                continue;
            }
            if (role.getSystemType() == SystemRoleType.SUPER_ADMIN) {
                return List.of(ALL_PERMISSION);
            }
            if (role.getPermissions() != null) {
                role.getPermissions().stream()
                        .map(PermissionID::getCode)
                        .forEach(permissions::add);
            }
        }
        return new ArrayList<>(permissions);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = getUser(loginId);
        if (user == null || user.getRoles() == null) {
            return List.of();
        }
        List<String> roles = new ArrayList<>();
        for (RoleID roleId : user.getRoles()) {
            Role role = roleRepo.findById(roleId.getValue()).orElse(null);
            if (role != null && role.getStatus() == RoleStatus.ACTIVE) {
                roles.add(role.getCode());
            }
        }
        return roles;
    }

    private User getUser(Object loginId) {
        if (loginId == null) {
            return null;
        }
        return userRepo.findById(Long.parseLong(String.valueOf(loginId))).orElse(null);
    }
}
