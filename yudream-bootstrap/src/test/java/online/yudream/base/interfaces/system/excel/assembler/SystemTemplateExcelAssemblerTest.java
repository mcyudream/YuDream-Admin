package online.yudream.base.interfaces.system.excel.assembler;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import online.yudream.base.application.system.template.cmd.SystemTemplateImportCmd;
import online.yudream.base.application.system.template.dto.SystemTemplateExportDTO;
import online.yudream.base.application.system.template.dto.TemplateDeptDTO;
import online.yudream.base.application.system.template.dto.TemplateMenuDTO;
import online.yudream.base.application.system.template.dto.TemplateRoleDTO;
import online.yudream.base.application.system.template.dto.TemplateUserDTO;
import online.yudream.base.application.system.template.service.SystemTemplateAppService;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.interfaces.system.excel.row.DeptExcelRow;
import online.yudream.base.interfaces.system.excel.row.MenuExcelRow;
import online.yudream.base.interfaces.system.excel.row.RoleExcelRow;
import online.yudream.base.interfaces.system.excel.row.SystemTemplateMetaExcelRow;
import online.yudream.base.interfaces.system.excel.row.UserExcelRow;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SystemTemplateExcelAssemblerTest {

    @Test
    void exportedWorkbookRowsRoundTripIntoPortableImportCommand() {
        SystemTemplateExportDTO source = new SystemTemplateExportDTO(
                List.of(new TemplateDeptDTO("9007199254740991", "/根部门/研发部", "研发部", "/根部门", "alice",
                        "13800000000", 10, "研发", "#2563eb", null, false, DeptStatus.ACTIVE)),
                List.of(new TemplateRoleDTO("9007199254740992", "developer", "开发", "/根部门/研发部", RoleLevel.USER,
                        List.of("system:menu:view"), "morph:code", "#2563eb", false, RoleStatus.ACTIVE)),
                List.of(new TemplateUserDTO("9007199254740993", "alice", "Alice", "alice@example.com", null, null,
                        true, UserStatus.ACTIVE, List.of("/根部门/研发部"), "/根部门/研发部", List.of("developer"))),
                List.of(new TemplateMenuDTO("9007199254740994", "system:demo", "示例", MenuNodeType.MENU, "system",
                        "system", "morph:file", "/system/demo", "system/demo/index.vue", null, 10, false,
                        "system:demo:view", MenuStatus.DISABLED))
        );

        byte[] bytes = writeWorkbook(SystemTemplateExcelAssembler.exportWorkbook(source));
        SystemTemplateImportCmd imported = readWorkbook(bytes);

        assertEquals(SystemTemplateAppService.FORMAT, imported.format());
        assertEquals(SystemTemplateAppService.SCHEMA_VERSION, imported.schemaVersion());
        assertEquals(9007199254740991L, imported.departments().getFirst().sourceId());
        assertEquals("/根部门/研发部", imported.roles().getFirst().deptPath());
        assertEquals(9007199254740993L, imported.users().getFirst().sourceId());
        assertEquals(List.of("developer"), imported.users().getFirst().roleCodes());
        assertFalse(imported.menus().getFirst().visible());
        assertEquals(MenuStatus.DISABLED, imported.menus().getFirst().status());
    }

    private byte[] writeWorkbook(List<online.yudream.base.interfaces.system.excel.support.ExcelHttpSupport.ExcelSheet<?>> sheets) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelWriter writer = EasyExcel.write(out).build();
        try {
            int index = 0;
            for (var sheet : sheets) {
                writer.write(sheet.rows(), EasyExcel.writerSheet(index++, sheet.name()).head(sheet.rowClass()).build());
            }
        }
        finally {
            writer.finish();
        }
        return out.toByteArray();
    }

    private SystemTemplateImportCmd readWorkbook(byte[] bytes) {
        List<SystemTemplateMetaExcelRow> meta = EasyExcel.read(new ByteArrayInputStream(bytes)).head(SystemTemplateMetaExcelRow.class)
                .sheet(SystemTemplateExcelAssembler.META_SHEET).doReadSync();
        List<DeptExcelRow> departments = EasyExcel.read(new ByteArrayInputStream(bytes)).head(DeptExcelRow.class)
                .sheet(SystemTemplateExcelAssembler.DEPT_SHEET).doReadSync();
        List<RoleExcelRow> roles = EasyExcel.read(new ByteArrayInputStream(bytes)).head(RoleExcelRow.class)
                .sheet(SystemTemplateExcelAssembler.ROLE_SHEET).doReadSync();
        List<UserExcelRow> users = EasyExcel.read(new ByteArrayInputStream(bytes)).head(UserExcelRow.class)
                .sheet(SystemTemplateExcelAssembler.USER_SHEET).doReadSync();
        List<MenuExcelRow> menus = EasyExcel.read(new ByteArrayInputStream(bytes)).head(MenuExcelRow.class)
                .sheet(SystemTemplateExcelAssembler.MENU_SHEET).doReadSync();
        return SystemTemplateExcelAssembler.toCommand(meta, departments, roles, users, menus);
    }
}
