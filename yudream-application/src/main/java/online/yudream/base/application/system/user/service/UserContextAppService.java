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
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.RoleID;
import online.yudream.base.domain.system.user.valobj.UserDept;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户当前上下文应用服务（部门/角色切换）。
 * <p>
 * 角色隶属于部门：先切部门，再切该部门拥有的角色，会话角色始终收敛在当前部门内。
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
        ResolvedDept current = currentDept(user, userId);
        return resolvedDepts(user).stream()
                .map(dept -> toDeptVO(dept, current != null && current.id().equals(dept.id())))
                .toList();
    }

    /**
     * 当前部门拥有的角色（用户已拥有且角色生效），供切换角色下拉使用。
     */
    @Transactional(readOnly = true)
    public List<UserRoleVO> listRoles(Long userId) {
        User user = getUser(userId);
        ResolvedDept dept = currentDept(user, userId);
        DeptID deptId = dept == null ? null : DeptID.of(dept.id());
        List<Role> ownedRoles = ownedRoles(user);
        RoleID currentRoleId = user.resolveContextRole(deptId, userContextStore.getCurrentRoleId(userId), ownedRoles);
        return rolesInDept(ownedRoles, deptId).stream()
                .map(role -> toRoleVO(role, currentRoleId != null && currentRoleId.getValue().equals(role.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserContextVO getContext(Long userId) {
        User user = getUser(userId);
        ResolvedDept dept = currentDept(user, userId);
        DeptID deptId = dept == null ? null : DeptID.of(dept.id());
        List<Role> ownedRoles = ownedRoles(user);
        RoleID roleId = user.resolveContextRole(deptId, userContextStore.getCurrentRoleId(userId), ownedRoles);
        Role role = roleId == null ? null : ownedRoles.stream()
                .filter(candidate -> candidate.getId().equals(roleId.getValue()))
                .findFirst()
                .orElse(null);
        return UserContextVO.builder()
                .currentDept(dept == null ? null : toDeptVO(dept, true))
                .currentRole(role == null ? null : toRoleVO(role, true))
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
        syncRoleToDept(user, userId, deptId);
    }

    @Transactional
    public void switchRole(Long userId, Long roleId) {
        User user = getUser(userId);
        boolean owned = user.getRoles() != null
                && user.getRoles().stream().anyMatch(rid -> rid.getValue().equals(roleId));
        if (!owned) {
            throw new BizException("未拥有该角色");
        }
        ResolvedDept dept = currentDept(user, userId);
        boolean inDept = dept != null && rolesInDept(ownedRoles(user), DeptID.of(dept.id())).stream()
                .anyMatch(role -> role.getId().equals(roleId));
        if (!inDept) {
            throw new BizException("该角色不属于当前部门");
        }
        userContextStore.setCurrentRole(userId, roleId);
    }

    /**
     * 切换部门后把会话角色收敛到新部门：原角色不属于新部门时改用该部门第一个可用角色。
     * 新部门没有可用角色时保留原角色，由 {@link User#resolveContextRole} 的回落分支兜底。
     */
    private void syncRoleToDept(User user, Long userId, Long deptId) {
        List<Role> roles = rolesInDept(ownedRoles(user), deptId == null ? null : DeptID.of(deptId));
        if (roles.isEmpty()) {
            return;
        }
        Long currentRoleId = userContextStore.getCurrentRoleId(userId);
        boolean available = currentRoleId != null && roles.stream().anyMatch(role -> role.getId().equals(currentRoleId));
        if (!available) {
            userContextStore.setCurrentRole(userId, roles.getFirst().getId());
        }
    }

    private UserDeptVO toDeptVO(ResolvedDept dept, boolean current) {
        return UserDeptVO.builder()
                .id(dept.id())
                .name(dept.dept().getName())
                .current(current)
                .defaultDept(dept.defaultDept())
                .build();
    }

    private UserRoleVO toRoleVO(Role role, boolean current) {
        return UserRoleVO.builder()
                .id(role.getId())
                .name(role.getName())
                .code(role.getCode())
                .deptId(role.getDeptId() == null ? null : role.getDeptId().getValue())
                .current(current)
                .build();
    }

    private User getUser(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
    }

    /**
     * 用户拥有的角色实体，顺序与用户角色清单一致（角色已删除的忽略）。
     */
    private List<Role> ownedRoles(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = user.getRoles().stream().map(RoleID::getValue).distinct().toList();
        Map<Long, Role> roleMap = roleRepo.findByIds(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity(), (left, right) -> left));
        return user.getRoles().stream()
                .map(roleId -> roleMap.get(roleId.getValue()))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 已在指定部门内生效的角色；部门为空时返回空清单。
     */
    private List<Role> rolesInDept(List<Role> ownedRoles, DeptID deptId) {
        if (deptId == null) {
            return List.of();
        }
        return ownedRoles.stream()
                .filter(role -> role.getStatus() == RoleStatus.ACTIVE)
                .filter(role -> role.getDeptId() != null && deptId.getValue().equals(role.getDeptId().getValue()))
                .toList();
    }

    private List<ResolvedDept> resolvedDepts(User user) {
        return user.getDepts().stream().map(this::resolveDept).toList();
    }

    /**
     * 有效当前部门：会话选择优先（原始 ID 与解析后 ID 均可命中），未选择或已失效时回落默认部门。
     */
    private ResolvedDept currentDept(User user, Long userId) {
        List<ResolvedDept> depts = resolvedDepts(user);
        if (depts.isEmpty()) {
            return null;
        }
        Long selectedDeptId = userContextStore.getCurrentDeptId(userId);
        if (selectedDeptId != null) {
            Optional<ResolvedDept> hit = depts.stream()
                    .filter(dept -> selectedDeptId.equals(dept.rawId()) || selectedDeptId.equals(dept.id()))
                    .findFirst();
            if (hit.isPresent()) {
                return hit.get();
            }
        }
        return depts.stream().filter(ResolvedDept::defaultDept).findFirst().orElse(depts.getFirst());
    }

    private ResolvedDept resolveDept(UserDept userDept) {
        Long rawId = userDept.id().getValue();
        return deptRepo.findById(rawId)
                .map(dept -> new ResolvedDept(rawId, rawId, userDept.isDefault(), dept))
                // Legacy users can retain an obsolete default-department ID after system data migration.
                .or(() -> userDept.isDefault()
                        ? deptRepo.findRoot().map(root -> new ResolvedDept(rawId, root.getId(), true, root))
                        : Optional.empty())
                .orElseThrow(() -> new BizException("部门不存在"));
    }

    private record ResolvedDept(Long rawId, Long id, boolean defaultDept, Dept dept) {}
}
