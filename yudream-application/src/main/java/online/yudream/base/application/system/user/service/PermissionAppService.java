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

        Set<String> permissions = new HashSet<>();
        for (RoleID roleId : user.getRoles()) {
            Role role = roleRepo.findById(roleId.getValue()).orElse(null);
            if (role == null || role.getStatus() != RoleStatus.ACTIVE) {
                continue;
            }
            if (role.getSystemType() == SystemRoleType.SUPER_ADMIN) {
                return List.of(ALL_PERMISSION);
            }
            if (role.getPermissions() == null) {
                continue;
            }
            permissions.addAll(role.getPermissions().stream()
                    .map(PermissionID::getCode)
                    .collect(Collectors.toSet()));
        }
        return new ArrayList<>(permissions);
    }
}
