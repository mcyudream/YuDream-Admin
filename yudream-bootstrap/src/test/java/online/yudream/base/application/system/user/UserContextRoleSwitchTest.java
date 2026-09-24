package online.yudream.base.application.system.user;

import online.yudream.base.application.system.user.service.PermissionAppService;
import online.yudream.base.application.system.user.service.UserContextAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.service.UserContextStore;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.PermissionID;
import online.yudream.base.domain.system.user.valobj.RoleID;
import online.yudream.base.domain.system.user.valobj.UserDept;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 部门/角色上下文：角色隶属于部门，先切部门，再切该部门拥有的角色。
 */
@ExtendWith(MockitoExtension.class)
class UserContextRoleSwitchTest {

    private static final Long USER_ID = 10L;
    private static final Long SYSTEM_ADMIN_DEPT_ID = 3L;
    private static final Long OPERATION_DEPT_ID = 4L;
    private static final Long SUPER_ADMIN_ROLE_ID = 21L;
    private static final Long OPERATOR_ROLE_ID = 22L;

    @Mock private UserRepo userRepo;
    @Mock private DeptRepo deptRepo;
    @Mock private RoleRepo roleRepo;
    @Mock private UserContextStore userContextStore;

    private UserContextAppService userContextAppService;

    @BeforeEach
    void setUp() {
        userContextAppService = new UserContextAppService(userRepo, deptRepo, roleRepo, userContextStore);
    }

    @Test
    void resolvesLegacyDefaultRootDepartmentReference() {
        User user = User.builder().id(10L).depts(List.of(new UserDept(DeptID.of(99L), true))).build();
        Dept root = Dept.builder().id(1L).name("根部门").deptType(SystemDeptType.ROOT).build();
        when(userRepo.findById(10L)).thenReturn(Optional.of(user));
        when(userContextStore.getCurrentDeptId(10L)).thenReturn(99L);
        when(deptRepo.findById(99L)).thenReturn(Optional.empty());
        when(deptRepo.findRoot()).thenReturn(Optional.of(root));

        var result = userContextAppService.listDepts(10L);

        assertThat(result).singleElement().satisfies(dept -> {
            assertThat(dept.getId()).isEqualTo(1L);
            assertThat(dept.getName()).isEqualTo("根部门");
            assertThat(dept.isCurrent()).isTrue();
        });
    }

    @Test
    void switchesToImplicitRootDepartmentForLegacyUser() {
        User user = User.builder().id(10L).depts(List.of(new UserDept(DeptID.of(99L), true))).build();
        Dept root = Dept.builder().id(1L).deptType(SystemDeptType.ROOT).build();
        when(userRepo.findById(10L)).thenReturn(Optional.of(user));
        when(deptRepo.findRoot()).thenReturn(Optional.of(root));

        userContextAppService.switchDept(10L, 1L);

        verify(userContextStore).setCurrentDept(10L, 1L);
    }

    @Test
    void limitsPermissionsToTheSelectedRole() {
        User user = User.builder().id(10L).emailVerified(true).status(UserStatus.ACTIVE)
                .roles(List.of(RoleID.of(SUPER_ADMIN_ROLE_ID), RoleID.of(OPERATOR_ROLE_ID))).build();
        when(userRepo.findById(10L)).thenReturn(Optional.of(user));
        when(userContextStore.getCurrentRoleId(10L)).thenReturn(SUPER_ADMIN_ROLE_ID);
        when(roleRepo.findByIds(anyList())).thenReturn(List.of(
                role(SUPER_ADMIN_ROLE_ID, "超级管理员", SYSTEM_ADMIN_DEPT_ID, "user:profile:view"),
                role(OPERATOR_ROLE_ID, "运营专员", OPERATION_DEPT_ID, "system:user:view")));

        var permissions = new PermissionAppService(userRepo, roleRepo, userContextStore).getUserPermissions(10L);

        assertThat(permissions).containsExactly("user:profile:view");
    }

    @Test
    void listsOnlyRolesOfTheSelectedDepartment() {
        stubTwoDeptUser();
        stubDeptLookup();
        when(userContextStore.getCurrentDeptId(USER_ID)).thenReturn(OPERATION_DEPT_ID);

        var result = userContextAppService.listRoles(USER_ID);

        assertThat(result).singleElement().satisfies(role -> {
            assertThat(role.getName()).isEqualTo("运营专员");
            assertThat(role.getDeptId()).isEqualTo(OPERATION_DEPT_ID);
            assertThat(role.isCurrent()).isTrue();
        });
    }

    @Test
    void reportsContextRoleWithinSelectedDepartment() {
        stubTwoDeptUser();
        stubDeptLookup();
        when(userContextStore.getCurrentDeptId(USER_ID)).thenReturn(SYSTEM_ADMIN_DEPT_ID);

        var context = userContextAppService.getContext(USER_ID);

        assertThat(context.getCurrentDept().getId()).isEqualTo(SYSTEM_ADMIN_DEPT_ID);
        assertThat(context.getCurrentRole().getId()).isEqualTo(SUPER_ADMIN_ROLE_ID);
        assertThat(context.getCurrentRole().getDeptId()).isEqualTo(SYSTEM_ADMIN_DEPT_ID);
    }

    @Test
    void switchingDepartmentMovesRoleToTheNewDepartment() {
        stubTwoDeptUser();
        when(userContextStore.getCurrentRoleId(USER_ID)).thenReturn(SUPER_ADMIN_ROLE_ID);

        userContextAppService.switchDept(USER_ID, OPERATION_DEPT_ID);

        verify(userContextStore).setCurrentDept(USER_ID, OPERATION_DEPT_ID);
        verify(userContextStore).setCurrentRole(USER_ID, OPERATOR_ROLE_ID);
    }

    @Test
    void keepsRoleWhenSwitchingDepartmentWithoutAvailableRole() {
        User user = User.builder().id(USER_ID)
                .depts(List.of(new UserDept(DeptID.of(SYSTEM_ADMIN_DEPT_ID), true), new UserDept(DeptID.of(OPERATION_DEPT_ID), false)))
                .roles(List.of(RoleID.of(SUPER_ADMIN_ROLE_ID)))
                .build();
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
        when(roleRepo.findByIds(anyList())).thenReturn(List.of(
                role(SUPER_ADMIN_ROLE_ID, "超级管理员", SYSTEM_ADMIN_DEPT_ID, "system:user:view")));

        userContextAppService.switchDept(USER_ID, OPERATION_DEPT_ID);

        verify(userContextStore).setCurrentDept(USER_ID, OPERATION_DEPT_ID);
    }

    @Test
    void rejectsRoleOutsideCurrentDepartment() {
        stubTwoDeptUser();
        stubDeptLookup();
        when(userContextStore.getCurrentDeptId(USER_ID)).thenReturn(OPERATION_DEPT_ID);

        assertThatThrownBy(() -> userContextAppService.switchRole(USER_ID, SUPER_ADMIN_ROLE_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不属于当前部门");
    }

    @Test
    void rejectsRoleTheUserDoesNotOwn() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(twoDeptUser().build()));

        assertThatThrownBy(() -> userContextAppService.switchRole(USER_ID, 99L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未拥有该角色");
    }

    @Test
    void permissionsFollowTheRoleOfTheCurrentDepartment() {
        stubTwoDeptUser();
        when(userContextStore.getCurrentDeptId(USER_ID)).thenReturn(OPERATION_DEPT_ID);

        var permissions = new PermissionAppService(userRepo, roleRepo, userContextStore).getUserPermissions(USER_ID);

        assertThat(permissions).containsExactly("launcher:server:view");
    }

    /**
     * 用户属于系统管理、运营两个部门，各自拥有落在本部门的角色。
     */
    private User.UserBuilder twoDeptUser() {
        return User.builder().id(USER_ID).emailVerified(true).status(UserStatus.ACTIVE)
                .depts(List.of(new UserDept(DeptID.of(SYSTEM_ADMIN_DEPT_ID), true), new UserDept(DeptID.of(OPERATION_DEPT_ID), false)))
                .roles(List.of(RoleID.of(SUPER_ADMIN_ROLE_ID), RoleID.of(OPERATOR_ROLE_ID)));
    }

    private void stubTwoDeptUser() {
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(twoDeptUser().build()));
        when(roleRepo.findByIds(anyList())).thenReturn(List.of(
                role(SUPER_ADMIN_ROLE_ID, "超级管理员", SYSTEM_ADMIN_DEPT_ID, "system:user:view"),
                role(OPERATOR_ROLE_ID, "运营专员", OPERATION_DEPT_ID, "launcher:server:view")));
    }

    private void stubDeptLookup() {
        when(deptRepo.findById(SYSTEM_ADMIN_DEPT_ID))
                .thenReturn(Optional.of(Dept.builder().id(SYSTEM_ADMIN_DEPT_ID).name("系统管理").build()));
        when(deptRepo.findById(OPERATION_DEPT_ID))
                .thenReturn(Optional.of(Dept.builder().id(OPERATION_DEPT_ID).name("运营部").build()));
    }

    private Role role(Long roleId, String name, Long deptId, String permission) {
        return Role.builder().id(roleId).name(name).code("role_" + roleId)
                .deptId(DeptID.of(deptId)).status(RoleStatus.ACTIVE)
                .permissions(List.of(PermissionID.of(permission))).build();
    }
}
