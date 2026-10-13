package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.cmd.UserRoleChangesCmd;
import online.yudream.base.application.system.user.dto.UserManageDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.repo.UserRoleChangesRepo;
import online.yudream.base.domain.system.user.valobj.RoleID;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserRoleChangesAppService {

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final UserRoleChangesRepo roleChangesRepo;
    private final UserManageAppService userManageAppService;

    public UserManageDTO apply(UserRoleChangesCmd cmd) {
        User user = userRepo.findById(cmd.getId()).orElseThrow(() -> new BizException("用户不存在"));
        List<RoleID> expected = cmd.getExpectedRoleIds() == null ? null : ids(cmd.getExpectedRoleIds());
        List<RoleID> grant = ids(cmd.getGrant());
        List<RoleID> revoke = ids(cmd.getRevoke());
        Set<RoleID> current = user.getRoles() == null ? Set.of() : Set.copyOf(user.getRoles());
        boolean changed = user.applyRoleChanges(expected, grant, revoke);
        if (changed) {
            List<Long> newIds = grant.stream().filter(id -> !current.contains(id)).map(RoleID::getValue).distinct().toList();
            List<Role> newRoles = newIds.isEmpty() ? List.of() : roleRepo.findByIds(newIds);
            if (newRoles.stream().map(Role::getId).distinct().count() != newIds.size()) {
                throw new BizException("新增分配的角色不存在");
            }
            if (newRoles.stream().anyMatch(role -> role.getStatus() != RoleStatus.ACTIVE)) {
                throw new BizException("新增分配的角色已停用");
            }
            Set<Long> deptIds = user.getDepts() == null ? Set.of() : user.getDepts().stream()
                    .map(dept -> dept.id().getValue()).collect(Collectors.toSet());
            if (newRoles.stream().anyMatch(role -> role.getDeptId() == null || !deptIds.contains(role.getDeptId().getValue()))) {
                throw new BizException("新增分配的角色必须属于用户已分配部门");
            }
            List<Long> requiredDeptIds = newRoles.stream().map(role -> role.getDeptId().getValue()).distinct().toList();
            boolean updated = roleChangesRepo.compareAndSet(user.getId(), expected.stream().map(RoleID::getValue).toList(),
                    user.getRoles().stream().map(RoleID::getValue).toList(), requiredDeptIds);
            if (!updated) {
                throw new BizException("用户角色或部门已变化，请刷新后重试");
            }
        }
        return userManageAppService.get(user.getId());
    }

    private List<RoleID> ids(List<String> values) {
        if (values == null) {
            return List.of();
        }
        try {
            return values.stream().map(Long::valueOf).map(RoleID::of).toList();
        } catch (NumberFormatException exception) {
            throw new BizException("角色标识无效");
        }
    }
}
