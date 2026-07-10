package online.yudream.base.application.system.user;

import online.yudream.base.application.system.user.service.DeptManageAppService;
import online.yudream.base.application.system.user.service.RoleManageAppService;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.PermissionRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.valobj.DeptID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManageEnableAppServiceTest {

    @Mock
    private RoleRepo roleRepo;
    @Mock
    private DeptRepo deptRepo;
    @Mock
    private PermissionRepo permissionRepo;
    @Mock
    private UserRepo userRepo;

    @Test
    void enableRoleOnlyChangesStatus() {
        Role role = Role.builder()
                .id(21L)
                .name("Auditor")
                .code("auditor")
                .deptId(DeptID.of(11L))
                .level(RoleLevel.USER)
                .status(RoleStatus.DEPRECATED)
                .build();
        when(roleRepo.findById(21L)).thenReturn(Optional.of(role));

        new RoleManageAppService(roleRepo, deptRepo, permissionRepo, userRepo).enable(21L);

        assertThat(role.getStatus()).isEqualTo(RoleStatus.ACTIVE);
        assertThat(role.getName()).isEqualTo("Auditor");
        assertThat(role.getCode()).isEqualTo("auditor");
        assertThat(role.getDeptId().getValue()).isEqualTo(11L);
        verify(roleRepo).save(role);
    }

    @Test
    void enableDeptOnlyChangesStatus() {
        Dept dept = Dept.builder()
                .id(11L)
                .name("Finance")
                .description("Finance team")
                .parentId(DeptID.of(1L))
                .sortOrder(7)
                .deptType(SystemDeptType.NORMAL)
                .status(DeptStatus.DEPRECATED)
                .build();
        when(deptRepo.findById(11L)).thenReturn(Optional.of(dept));

        new DeptManageAppService(deptRepo, userRepo).enable(11L);

        assertThat(dept.getStatus()).isEqualTo(DeptStatus.ACTIVE);
        assertThat(dept.getName()).isEqualTo("Finance");
        assertThat(dept.getDescription()).isEqualTo("Finance team");
        assertThat(dept.getParentId().getValue()).isEqualTo(1L);
        assertThat(dept.getSortOrder()).isEqualTo(7);
        verify(deptRepo).save(dept);
    }
}
