package online.yudream.base.application.system.user;

import online.yudream.base.application.system.user.service.PermissionAppService;
import online.yudream.base.application.system.user.service.UserContextAppService;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserContextRoleSwitchTest {

    @Mock private UserRepo userRepo;
    @Mock private DeptRepo deptRepo;
    @Mock private RoleRepo roleRepo;
    @Mock private UserContextStore userContextStore;

    @Test
    void resolvesLegacyDefaultRootDepartmentReference() {
        User user = User.builder().id(10L).depts(List.of(new UserDept(DeptID.of(99L), true))).build();
        Dept root = Dept.builder().id(1L).name("根部门").deptType(SystemDeptType.ROOT).build();
        when(userRepo.findById(10L)).thenReturn(Optional.of(user));
        when(userContextStore.getCurrentDeptId(10L)).thenReturn(99L);
        when(deptRepo.findById(99L)).thenReturn(Optional.empty());
        when(deptRepo.findRoot()).thenReturn(Optional.of(root));

        var result = new UserContextAppService(userRepo, deptRepo, roleRepo, userContextStore).listDepts(10L);

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

        new UserContextAppService(userRepo, deptRepo, roleRepo, userContextStore).switchDept(10L, 1L);

        verify(userContextStore).setCurrentDept(10L, 1L);
    }

    @Test
    void limitsPermissionsToTheSelectedRole() {
        RoleID userRoleId = RoleID.of(21L);
        RoleID adminRoleId = RoleID.of(22L);
        User user = User.builder().id(10L).emailVerified(true).status(UserStatus.ACTIVE).roles(List.of(userRoleId, adminRoleId)).build();
        Role userRole = Role.builder().id(21L).status(RoleStatus.ACTIVE).permissions(List.of(PermissionID.of("user:profile:view"))).build();
        when(userRepo.findById(10L)).thenReturn(Optional.of(user));
        when(userContextStore.getCurrentRoleId(10L)).thenReturn(21L);
        when(roleRepo.findById(21L)).thenReturn(Optional.of(userRole));
        var permissions = new PermissionAppService(userRepo, roleRepo, userContextStore).getUserPermissions(10L);

        assertThat(permissions).containsExactly("user:profile:view");
    }
}
