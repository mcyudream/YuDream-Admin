package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.service.UserContextStore;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.PermissionID;
import online.yudream.base.domain.system.user.valobj.RoleID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限应用服务。
 */
@Service
@RequiredArgsConstructor
public class PermissionAppService {

    private static final String ALL_PERMISSION = "*";

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final UserContextStore userContextStore;

    /**
     * 获取用户拥有的全部权限码（汇总所有角色）。
     */
    @Transactional(readOnly = true)
    public List<String> getUserPermissions(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        if (user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            return List.of();
        }

        Role role = currentRole(user, userId);
        if (role == null || role.getStatus() != RoleStatus.ACTIVE) {
            return List.of();
        }
        if (role.getSystemType() == SystemRoleType.SUPER_ADMIN) {
            return List.of(ALL_PERMISSION);
        }
        if (role.getPermissions() == null) {
            return List.of();
        }
        return role.getPermissions().stream()
                .map(PermissionID::getCode)
                .collect(Collectors.toList());
    }

    /**
     * 会话生效角色：角色隶属于部门，按「当前部门 → 已拥有角色」顺序解析，
     * 与切换部门/角色（{@link UserContextAppService}）保持同一套口径。
     */
    private Role currentRole(User user, Long userId) {
        List<RoleID> roles = user.getRoles();
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        List<Role> ownedRoles = roleRepo.findByIds(roles.stream().map(RoleID::getValue).distinct().toList());
        DeptID deptId = user.resolveContextDeptID(userContextStore.getCurrentDeptId(userId));
        RoleID roleId = user.resolveContextRole(deptId, userContextStore.getCurrentRoleId(userId), ownedRoles);
        if (roleId == null) {
            return null;
        }
        return ownedRoles.stream().filter(role -> role.getId().equals(roleId.getValue())).findFirst().orElse(null);
    }
}
