package online.yudream.base.application.system.user.service;

import online.yudream.base.application.system.user.cmd.TemplateDeptUpsertCmd;
import online.yudream.base.application.system.user.cmd.TemplateRoleUpsertCmd;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SingleSheetTemplateImportTest {

    @Test
    void roleFallsBackToBusinessCodeWhenSourceIdBelongsToOtherRecord() {
        RoleRepo roleRepo = mock(RoleRepo.class);
        DeptRepo deptRepo = mock(DeptRepo.class);
        PermissionRepo permissionRepo = mock(PermissionRepo.class);
        UserRepo userRepo = mock(UserRepo.class);
        Role codeTarget = Role.builder().id(11L).code("ops").name("原角色")
                .deptId(DeptID.of(2L)).level(RoleLevel.USER).status(RoleStatus.ACTIVE).build();
        when(roleRepo.findById(99L)).thenReturn(Optional.of(Role.builder().id(99L).code("other").build()));
        when(roleRepo.findByCode("ops")).thenReturn(Optional.of(codeTarget));
        when(roleRepo.findById(11L)).thenReturn(Optional.of(codeTarget));
        when(roleRepo.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateRoleUpsertCmd cmd = new TemplateRoleUpsertCmd();
        cmd.setSourceId(99L);
        cmd.setCode("ops");
        cmd.setName("运营角色");

        var result = new RoleManageAppService(roleRepo, deptRepo, permissionRepo, userRepo).importTemplateRow(cmd);

        assertFalse(result.isCreated());
    }

    @Test
    void deptPrefersSameNameAndParentBusinessKeyOverConflictingSourceId() {
        DeptRepo deptRepo = mock(DeptRepo.class);
        UserRepo userRepo = mock(UserRepo.class);
        Dept businessTarget = Dept.builder().id(11L).name("研发")
                .deptType(SystemDeptType.NORMAL).status(DeptStatus.ACTIVE).build();
        when(deptRepo.findById(99L)).thenReturn(Optional.of(Dept.builder().id(99L).name("其他")
                .deptType(SystemDeptType.NORMAL).status(DeptStatus.ACTIVE).build()));
        when(deptRepo.findByNameAndParent("研发", null)).thenReturn(Optional.of(businessTarget));
        when(deptRepo.findById(11L)).thenReturn(Optional.of(businessTarget));
        when(deptRepo.existsByNameAndParentExcludeId("研发", null, 11L)).thenReturn(false);
        when(deptRepo.save(any(Dept.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TemplateDeptUpsertCmd cmd = new TemplateDeptUpsertCmd();
        cmd.setSourceId(99L);
        cmd.setName("研发");
        cmd.setParentSpecified(true);

        var result = new DeptManageAppService(deptRepo, userRepo, null).importTemplateRow(cmd);

        assertFalse(result.isCreated());
    }

    @Test
    void roleCreatesWhenNeitherIdNorCodeMatches() {
        RoleRepo roleRepo = mock(RoleRepo.class);
        DeptRepo deptRepo = mock(DeptRepo.class);
        PermissionRepo permissionRepo = mock(PermissionRepo.class);
        UserRepo userRepo = mock(UserRepo.class);
        when(roleRepo.findById(99L)).thenReturn(Optional.empty());
        when(roleRepo.findByCode("ops")).thenReturn(Optional.empty());
        when(roleRepo.existsByCodeExcludeId("ops", null)).thenReturn(false);
        when(deptRepo.findById(2L)).thenReturn(Optional.of(Dept.builder().id(2L).build()));
        when(roleRepo.save(any(Role.class))).thenReturn(Role.builder().id(12L).code("ops").name("运营")
                .deptId(DeptID.of(2L)).status(RoleStatus.ACTIVE).build());

        TemplateRoleUpsertCmd cmd = new TemplateRoleUpsertCmd();
        cmd.setSourceId(99L);
        cmd.setCode("ops");
        cmd.setName("运营");
        cmd.setDeptId(2L);

        var result = new RoleManageAppService(roleRepo, deptRepo, permissionRepo, userRepo).importTemplateRow(cmd);

        assertTrue(result.isCreated());
    }
}
