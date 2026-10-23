package online.yudream.base.application.system.template.service;

import online.yudream.base.application.system.menu.service.MenuAppService;
import online.yudream.base.application.system.template.cmd.TemplateDeptImportCmd;
import online.yudream.base.application.system.template.cmd.TemplateMenuImportCmd;
import online.yudream.base.application.system.template.cmd.TemplateRoleImportCmd;
import online.yudream.base.application.system.template.dto.SystemTemplateImportResultDTO;
import online.yudream.base.application.system.user.cmd.TemplateRoleUpsertCmd;
import online.yudream.base.application.system.user.dto.TemplateDeptUpsertResult;
import online.yudream.base.application.system.user.dto.TemplateRoleUpsertResult;
import online.yudream.base.application.system.user.service.DeptManageAppService;
import online.yudream.base.application.system.user.service.RoleManageAppService;
import online.yudream.base.application.system.user.service.TemplateUserAppService;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.valobj.DeptID;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 资源页 Excel 批量导入的行为验证：业务键回退、重复身份预检与层级乱序容忍。
 */
class SystemResourceImportTest {

    @Test
    void roleImportFallsBackToBusinessCodeWhenSourceIdBelongsToOtherRecord() {
        RoleRepo roleRepo = mock(RoleRepo.class);
        DeptRepo deptRepo = mock(DeptRepo.class);
        RoleManageAppService roleService = mock(RoleManageAppService.class);
        Dept existing = Dept.builder().id(2L).name("研发")
                .deptType(SystemDeptType.NORMAL).status(online.yudream.base.domain.system.user.enumerate.DeptStatus.ACTIVE).build();
        Role codeTarget = Role.builder().id(11L).code("ops").name("原角色")
                .deptId(DeptID.of(2L)).level(RoleLevel.USER).status(RoleStatus.ACTIVE).build();
        when(deptRepo.findAll()).thenReturn(List.of(existing));
        when(roleRepo.findAll()).thenReturn(List.of());
        when(roleRepo.findById(99L)).thenReturn(Optional.of(Role.builder().id(99L).code("other").build()));
        when(roleRepo.findByCode("ops")).thenReturn(Optional.of(codeTarget));
        when(roleService.applyTemplateUpdate(eq(11L), any(TemplateRoleUpsertCmd.class)))
                .thenReturn(TemplateRoleUpsertResult.builder().created(false).roleId(11L).code("ops").build());

        SystemTemplateAppService service = service(roleRepo, deptRepo, roleService);
        SystemTemplateImportResultDTO result = service.importRoles(List.of(roleRow(99L, "ops")));

        assertEquals(0, result.failed(), () -> "失败详情：" + result.errors());
        assertEquals(1, result.updated());
        assertFalse(result.created() > 0);
    }

    @Test
    void deptImportKeepsRowsWhenParentAppearsLater() {
        RoleRepo roleRepo = mock(RoleRepo.class);
        DeptRepo deptRepo = mock(DeptRepo.class);
        RoleManageAppService roleService = mock(RoleManageAppService.class);
        DeptManageAppService deptService = mock(DeptManageAppService.class);
        when(deptRepo.findAll()).thenReturn(List.of());
        when(roleRepo.findAll()).thenReturn(List.of());
        TemplateDeptImportCmd parent = deptRow("/研发中心", "研发中心", null);
        TemplateDeptImportCmd child = deptRow("/研发中心/后端组", "后端组", "/研发中心");
        when(deptService.createFromTemplate(any())).thenAnswer(invocation ->
                TemplateDeptUpsertResult.builder().created(true).deptId(1L).build());

        SystemTemplateAppService service = service(roleRepo, deptRepo, roleService, deptService);
        // 子部门先于父部门出现，导入仍应成功
        SystemTemplateImportResultDTO result = service.importDepartments(List.of(child, parent));

        assertEquals(0, result.failed(), () -> "失败详情：" + result.errors());
        assertEquals(2, result.created());
    }

    @Test
    void menuImportRejectsDuplicateCodesBeforeAnyWrite() {
        SystemTemplateAppService service = service(mock(RoleRepo.class), mock(DeptRepo.class),
                mock(RoleManageAppService.class), mock(DeptManageAppService.class));
        TemplateMenuImportCmd first = menuRow("system:demo");
        TemplateMenuImportCmd second = menuRow("system:demo");

        SystemTemplateImportResultDTO result = service.importMenus(List.of(first, second));

        assertEquals(1, result.failed());
        assertEquals(0, result.success());
        assertTrue(result.errors().stream().allMatch(issue -> "菜单".equals(issue.sheet())));
        assertTrue(result.errors().stream().anyMatch(issue -> issue.message().contains("菜单编码重复")));
    }

    private TemplateRoleImportCmd roleRow(Long sourceId, String code) {
        return new TemplateRoleImportCmd(sourceId, code, "运营角色", "/研发", RoleLevel.USER,
                List.of(), null, null, false, null);
    }

    private TemplateDeptImportCmd deptRow(String path, String name, String parentPath) {
        return new TemplateDeptImportCmd(null, path, name, parentPath, null,
                null, 0, null, null, false, null);
    }

    private TemplateMenuImportCmd menuRow(String code) {
        return new TemplateMenuImportCmd(null, code, "演示", null,
                null, null, null, null, null, null, null, null, null, null);
    }

    private SystemTemplateAppService service(RoleRepo roleRepo, DeptRepo deptRepo, RoleManageAppService roleService) {
        return service(roleRepo, deptRepo, roleService, mock(DeptManageAppService.class));
    }

    private SystemTemplateAppService service(RoleRepo roleRepo, DeptRepo deptRepo, RoleManageAppService roleService,
                                             DeptManageAppService deptService) {
        UserRepo userRepo = mock(UserRepo.class);
        when(userRepo.page(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new online.yudream.base.domain.common.PageResult<>(List.of(), 0, 1, 200));
        return new SystemTemplateAppService(deptRepo, roleRepo, userRepo, mock(MenuRepo.class),
                deptService, roleService, mock(TemplateUserAppService.class), mock(MenuAppService.class));
    }
}
