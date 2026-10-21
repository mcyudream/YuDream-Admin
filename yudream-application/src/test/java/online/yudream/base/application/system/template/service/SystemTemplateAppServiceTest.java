package online.yudream.base.application.system.template.service;

import online.yudream.base.application.system.menu.service.MenuAppService;
import online.yudream.base.application.system.template.cmd.SystemTemplateImportCmd;
import online.yudream.base.application.system.template.cmd.TemplateDeptImportCmd;
import online.yudream.base.application.system.template.cmd.TemplateMenuImportCmd;
import online.yudream.base.application.system.template.cmd.TemplateRoleImportCmd;
import online.yudream.base.application.system.template.cmd.TemplateUserImportCmd;
import online.yudream.base.application.system.user.service.DeptManageAppService;
import online.yudream.base.application.system.user.service.RoleManageAppService;
import online.yudream.base.application.system.user.service.TemplateUserAppService;
import online.yudream.base.domain.system.menu.repo.MenuRepo;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SystemTemplateAppServiceTest {

    @Test
    void rejectsUnknownFormatBeforeReadingAnyRepository() {
        SystemTemplateAppService service = service();
        SystemTemplateImportCmd command = new SystemTemplateImportCmd("foreign-template", 1,
                List.of(), List.of(), List.of(), List.of());

        var result = service.importTemplate(command);

        assertEquals(0, result.success());
        assertEquals(1, result.failed());
        assertEquals(1, result.errors().size());
        assertEquals("说明", result.errors().getFirst().sheet());
        assertTrue(result.errors().getFirst().message().contains("格式不受支持"));
    }

    @Test
    void rejectsFutureSchemaVersion() {
        SystemTemplateAppService service = service();
        SystemTemplateImportCmd command = new SystemTemplateImportCmd(SystemTemplateAppService.FORMAT, 2,
                List.of(), List.of(), List.of(), List.of());

        var result = service.importTemplate(command);

        assertEquals(0, result.success());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().message().contains("协议版本过新"));
    }

    @Test
    void rejectsDuplicateNormalizedDepartmentPathsBeforeWrites() {
        SystemTemplateAppService service = service();
        TemplateDeptImportCmd first = new TemplateDeptImportCmd(null, "/研发", "研发", null, null,
                null, 0, null, null, false, null);
        TemplateDeptImportCmd second = new TemplateDeptImportCmd(null, "/研发/", "研发", null, null,
                null, 0, null, null, false, null);
        SystemTemplateImportCmd command = new SystemTemplateImportCmd(SystemTemplateAppService.FORMAT, 1,
                List.of(first, second), List.of(), List.of(), List.of());

        var result = service.importTemplate(command);

        assertEquals(1, result.failed());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().stream().allMatch(issue -> "部门".equals(issue.sheet())));
        assertTrue(result.errors().stream().anyMatch(issue -> issue.message().contains("部门路径重复")));
    }

    @Test
    void rejectsDuplicateStableIdentityBeforeWrites() {
        SystemTemplateAppService service = service();
        TemplateMenuImportCmd first = new TemplateMenuImportCmd(null, "system:demo", "演示", null,
                null, null, null, null, null, null, null, null, null, null);
        TemplateMenuImportCmd second = new TemplateMenuImportCmd(null, "system:demo", "演示二", null,
                null, null, null, null, null, null, null, null, null, null);
        SystemTemplateImportCmd command = new SystemTemplateImportCmd(SystemTemplateAppService.FORMAT, 1,
                List.of(), List.of(), List.of(), List.of(first, second));

        var result = service.importTemplate(command);

        assertEquals(1, result.failed());
        assertEquals(1, result.errors().size());
        assertEquals("菜单", result.errors().getFirst().sheet());
        assertTrue(result.errors().getFirst().message().contains("菜单编码重复"));
    }

    private SystemTemplateAppService service() {
        return new SystemTemplateAppService(
                mock(DeptRepo.class), mock(RoleRepo.class), mock(UserRepo.class), mock(MenuRepo.class),
                mock(DeptManageAppService.class), mock(RoleManageAppService.class),
                mock(TemplateUserAppService.class), mock(MenuAppService.class));
    }
}
