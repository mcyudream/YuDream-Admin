package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.dto.UserContextVO;
import online.yudream.base.application.system.user.dto.UserDeptVO;
import online.yudream.base.application.system.user.dto.UserRoleVO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.service.UserContextStore;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.RoleID;
import online.yudream.base.domain.system.user.valobj.UserDept;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户当前上下文应用服务（部门/角色切换）。
 */
@Service
@RequiredArgsConstructor
public class UserContextAppService {

    private final UserRepo userRepo;
    private final DeptRepo deptRepo;
    private final RoleRepo roleRepo;
    private final UserContextStore userContextStore;

    @Transactional(readOnly = true)
    public List<UserDeptVO> listDepts(Long userId) {
        User user = getUser(userId);
        Long currentDeptId = userContextStore.getCurrentDeptId(userId);
        if (currentDeptId == null) {
            currentDeptId = user.getDefaultDeptID() == null ? null : user.getDefaultDeptID().getValue();
        }
        final Long selectedDeptId = currentDeptId;
        return user.getDepts().stream().map(userDept -> {
            ResolvedDept dept = resolveDept(userDept);
            return UserDeptVO.builder()
                    .id(dept.id())
                    .name(dept.dept().getName())
                    .current(selectedDeptId != null && (selectedDeptId.equals(userDept.id().getValue()) || selectedDeptId.equals(dept.id())))
                    .defaultDept(userDept.isDefault())
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<UserRoleVO> listRoles(Long userId) {
        User user = getUser(userId);
        Long currentRoleId = userContextStore.getCurrentRoleId(userId);
        if (currentRoleId == null && !user.getRoles().isEmpty()) {
            currentRoleId = user.getRoles().get(0).getValue();
        }
        final Long selectedRoleId = currentRoleId;
        return user.getRoles().stream().map(roleId -> {
            Role role = roleRepo.findById(roleId.getValue())
                    .orElseThrow(() -> new BizException("角色不存在"));
            return UserRoleVO.builder()
                    .id(role.getId())
                    .name(role.getName())
                    .code(role.getCode())
                    .current(selectedRoleId != null && selectedRoleId.equals(role.getId()))
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public UserContextVO getContext(Long userId) {
        List<UserDeptVO> depts = listDepts(userId);
        List<UserRoleVO> roles = listRoles(userId);
        return UserContextVO.builder()
                .currentDept(depts.stream().filter(UserDeptVO::isCurrent).findFirst().orElse(null))
                .currentRole(roles.stream().filter(UserRoleVO::isCurrent).findFirst().orElse(null))
                .build();
    }

    @Transactional
    public void switchDept(Long userId, Long deptId) {
        User user = getUser(userId);
        boolean rootDept = deptRepo.findRoot().map(Dept::getId).filter(deptId::equals).isPresent();
        if (!user.belongsToDept(DeptID.of(deptId)) && !rootDept) {
            throw new BizException("未加入该部门");
        }
        userContextStore.setCurrentDept(userId, deptId);
    }

    @Transactional
    public void switchRole(Long userId, Long roleId) {
        User user = getUser(userId);
        boolean owned = user.getRoles().stream().anyMatch(rid -> rid.getValue().equals(roleId));
        if (!owned) {
            throw new BizException("未拥有该角色");
        }
        userContextStore.setCurrentRole(userId, roleId);
    }

    private User getUser(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
    }

    private ResolvedDept resolveDept(UserDept userDept) {
        return deptRepo.findById(userDept.id().getValue())
                .map(dept -> new ResolvedDept(userDept.id().getValue(), dept))
                // Legacy users can retain an obsolete default-department ID after system data migration.
                .or(() -> userDept.isDefault()
                        ? deptRepo.findRoot().map(root -> new ResolvedDept(root.getId(), root))
                        : java.util.Optional.empty())
                .orElseThrow(() -> new BizException("部门不存在"));
    }

    private record ResolvedDept(Long id, Dept dept) {}
}
