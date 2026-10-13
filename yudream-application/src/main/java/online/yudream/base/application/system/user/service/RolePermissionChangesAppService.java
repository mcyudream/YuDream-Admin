package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.assembler.UserAssembler;
import online.yudream.base.application.system.user.cmd.RolePermissionChangesCmd;
import online.yudream.base.application.system.user.dto.RoleManageDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Permission;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.enumerate.PermissionStatus;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.PermissionRepo;
import online.yudream.base.domain.system.user.repo.RolePermissionChangesRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.valobj.PermissionID;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RolePermissionChangesAppService {

    private final RoleRepo roleRepo;
    private final PermissionRepo permissionRepo;
    private final DeptRepo deptRepo;
    private final RolePermissionChangesRepo permissionChangesRepo;

    public RoleManageDTO apply(RolePermissionChangesCmd cmd) {
        Role role = roleRepo.findById(cmd.getId()).orElseThrow(() -> new BizException("角色不存在"));
        List<PermissionID> expected = cmd.getExpectedPermissions() == null ? null : ids(cmd.getExpectedPermissions());
        List<PermissionID> grant = ids(cmd.getGrant());
        List<PermissionID> revoke = ids(cmd.getRevoke());
        Set<String> currentCodes = role.getPermissions() == null ? Set.of() : role.getPermissions().stream()
                .map(PermissionID::getCode).collect(Collectors.toSet());
        boolean changed = role.applyPermissionChanges(expected, grant, revoke);
        if (changed) {
            Set<String> activeCodes = permissionRepo.findActive().stream()
                    .filter(permission -> permission.getStatus() == PermissionStatus.ACTIVE)
                    .map(Permission::getId).map(PermissionID::getCode).collect(Collectors.toSet());
            for (PermissionID permission : grant) {
                if (!currentCodes.contains(permission.getCode()) && !activeCodes.contains(permission.getCode())) {
                    throw new BizException("只能授予当前有效的已登记权限：" + permission.getCode());
                }
            }
            // 单个角色的授权数组 CAS，不把跨角色逐项提交包装成数据库事务。
            role = permissionChangesRepo.compareAndSet(role.getId(), cmd.getExpectedPermissions(),
                            role.getPermissions().stream().map(PermissionID::getCode).toList())
                    .orElseThrow(() -> new BizException("角色授权已变化，请刷新后重试"));
        }
        Map<Long, Dept> departments = role.getDeptId() == null ? Map.of() : deptRepo.findByIds(List.of(role.getDeptId().getValue()))
                .stream().collect(Collectors.toMap(Dept::getId, dept -> dept, (left, right) -> left));
        return UserAssembler.toRoleManageDTO(role, departments);
    }

    private List<PermissionID> ids(List<String> codes) {
        return codes == null ? List.of() : codes.stream().map(PermissionID::of).toList();
    }
}
