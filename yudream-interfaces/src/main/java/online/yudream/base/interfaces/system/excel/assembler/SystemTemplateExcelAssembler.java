package online.yudream.base.interfaces.system.excel.assembler;

import online.yudream.base.application.system.template.cmd.SystemTemplateImportCmd;
import online.yudream.base.application.system.template.cmd.TemplateDeptImportCmd;
import online.yudream.base.application.system.template.cmd.TemplateMenuImportCmd;
import online.yudream.base.application.system.template.cmd.TemplateRoleImportCmd;
import online.yudream.base.application.system.template.cmd.TemplateUserImportCmd;
import online.yudream.base.application.system.template.dto.SystemTemplateExportDTO;
import online.yudream.base.application.system.template.dto.SystemTemplateImportResultDTO;
import online.yudream.base.application.system.template.dto.SystemTemplateIssueDTO;
import online.yudream.base.application.system.template.dto.TemplateDeptDTO;
import online.yudream.base.application.system.template.dto.TemplateMenuDTO;
import online.yudream.base.application.system.template.dto.TemplateRoleDTO;
import online.yudream.base.application.system.template.dto.TemplateUserDTO;
import online.yudream.base.application.system.template.service.SystemTemplateAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.interfaces.system.excel.res.ExcelImportResultRes;
import online.yudream.base.interfaces.system.excel.row.DeptExcelRow;
import online.yudream.base.interfaces.system.excel.row.MenuExcelRow;
import online.yudream.base.interfaces.system.excel.row.RoleExcelRow;
import online.yudream.base.interfaces.system.excel.row.SystemTemplateMetaExcelRow;
import online.yudream.base.interfaces.system.excel.row.UserExcelRow;
import online.yudream.base.interfaces.system.excel.support.ExcelHttpSupport;

import java.util.List;

/** 版本化系统数据模板包的 Excel 边界转换器。 */
public final class SystemTemplateExcelAssembler {
    public static final String META_SHEET = "说明";
    public static final String DEPT_SHEET = "部门";
    public static final String ROLE_SHEET = "角色";
    public static final String USER_SHEET = "用户";
    public static final String MENU_SHEET = "菜单";

    private SystemTemplateExcelAssembler() {
    }

    public static List<ExcelHttpSupport.ExcelSheet<?>> blankWorkbook() {
        return workbookRows(List.of(), List.of(), List.of(), List.of());
    }

    public static List<ExcelHttpSupport.ExcelSheet<?>> exportWorkbook(SystemTemplateExportDTO dto) {
        List<DeptExcelRow> departments = dto == null || dto.departments() == null ? List.of()
                : dto.departments().stream().map(SystemTemplateExcelAssembler::toRow).toList();
        List<RoleExcelRow> roles = dto == null || dto.roles() == null ? List.of()
                : dto.roles().stream().map(SystemTemplateExcelAssembler::toRow).toList();
        List<UserExcelRow> users = dto == null || dto.users() == null ? List.of()
                : dto.users().stream().map(SystemTemplateExcelAssembler::toRow).toList();
        List<MenuExcelRow> menus = dto == null || dto.menus() == null ? List.of()
                : dto.menus().stream().map(SystemTemplateExcelAssembler::toRow).toList();
        return workbookRows(departments, roles, users, menus);
    }

    public static List<ExcelHttpSupport.ExcelSheet<?>> workbookDefinition() {
        return List.of(
                new ExcelHttpSupport.ExcelSheet<>(META_SHEET, SystemTemplateMetaExcelRow.class),
                new ExcelHttpSupport.ExcelSheet<>(DEPT_SHEET, DeptExcelRow.class),
                new ExcelHttpSupport.ExcelSheet<>(ROLE_SHEET, RoleExcelRow.class),
                new ExcelHttpSupport.ExcelSheet<>(USER_SHEET, UserExcelRow.class),
                new ExcelHttpSupport.ExcelSheet<>(MENU_SHEET, MenuExcelRow.class)
        );
    }

    public static SystemTemplateImportCmd toCommand(List<SystemTemplateMetaExcelRow> metaRows,
                                                     List<DeptExcelRow> departments,
                                                     List<RoleExcelRow> roles,
                                                     List<UserExcelRow> users,
                                                     List<MenuExcelRow> menus) {
        if (metaRows == null || metaRows.size() != 1) {
            throw new BizException("模板说明工作表必须且只能包含一条格式元数据");
        }
        SystemTemplateMetaExcelRow meta = metaRows.getFirst();
        String format = SystemExcelAssembler.required(meta.getFormat(), "模板格式");
        Long version = SystemExcelAssembler.requiredLong(meta.getSchemaVersion(), "模板协议版本");
        if (version > Integer.MAX_VALUE) {
            throw new BizException("模板协议版本无效");
        }
        return new SystemTemplateImportCmd(
                format,
                version.intValue(),
                safe(departments).stream().map(SystemTemplateExcelAssembler::toDeptCmd).toList(),
                safe(roles).stream().map(SystemTemplateExcelAssembler::toRoleCmd).toList(),
                safe(users).stream().map(SystemTemplateExcelAssembler::toUserCmd).toList(),
                safe(menus).stream().map(SystemTemplateExcelAssembler::toMenuCmd).toList()
        );
    }

    public static ExcelImportResultRes toRes(SystemTemplateImportResultDTO result) {
        if (result == null) {
            return ExcelImportResultRes.builder().build();
        }
        return ExcelImportResultRes.builder()
                .total(result.total())
                .success(result.success())
                .failed(result.failed())
                .created(result.created())
                .updated(result.updated())
                .skipped(result.skipped())
                .errors(safe(result.errors()).stream().map(SystemTemplateExcelAssembler::issueText).toList())
                .warnings(safe(result.warnings()).stream().map(SystemTemplateExcelAssembler::issueText).toList())
                .build();
    }

    private static List<ExcelHttpSupport.ExcelSheet<?>> workbookRows(List<DeptExcelRow> departments,
                                                                       List<RoleExcelRow> roles,
                                                                       List<UserExcelRow> users,
                                                                       List<MenuExcelRow> menus) {
        SystemTemplateMetaExcelRow meta = new SystemTemplateMetaExcelRow();
        meta.setFormat(SystemTemplateAppService.FORMAT);
        meta.setSchemaVersion(String.valueOf(SystemTemplateAppService.SCHEMA_VERSION));
        meta.setDescription("仅导入部门、角色、用户、菜单的受控字段；不会删除模板外记录。完整实例迁移请使用 YDBA 数据备份。");
        return List.of(
                new ExcelHttpSupport.ExcelSheet<>(META_SHEET, SystemTemplateMetaExcelRow.class, List.of(meta)),
                new ExcelHttpSupport.ExcelSheet<>(DEPT_SHEET, DeptExcelRow.class, safe(departments)),
                new ExcelHttpSupport.ExcelSheet<>(ROLE_SHEET, RoleExcelRow.class, safe(roles)),
                new ExcelHttpSupport.ExcelSheet<>(USER_SHEET, UserExcelRow.class, safe(users)),
                new ExcelHttpSupport.ExcelSheet<>(MENU_SHEET, MenuExcelRow.class, safe(menus))
        );
    }

    private static TemplateDeptImportCmd toDeptCmd(DeptExcelRow row) {
        return new TemplateDeptImportCmd(
                SystemExcelAssembler.parseLong(row.getId(), "部门ID"),
                SystemExcelAssembler.required(row.getPath(), "部门路径"),
                SystemExcelAssembler.required(row.getName(), "部门名称"),
                SystemExcelAssembler.trimToNull(row.getParentPath()),
                SystemExcelAssembler.trimToNull(row.getLeaderUsername()),
                SystemExcelAssembler.trimToNull(row.getPhone()),
                row.getSortOrder(),
                SystemExcelAssembler.trimToNull(row.getDescription()),
                SystemExcelAssembler.trimToNull(row.getColor()),
                Boolean.TRUE.equals(SystemExcelAssembler.parseNullableBoolean(row.getSystemDept(), "系统部门")),
                SystemExcelAssembler.parseEnum(row.getStatus(), DeptStatus.class, DeptStatus.ACTIVE, "部门状态")
        );
    }

    private static TemplateRoleImportCmd toRoleCmd(RoleExcelRow row) {
        return new TemplateRoleImportCmd(
                SystemExcelAssembler.parseLong(row.getId(), "角色ID"),
                SystemExcelAssembler.required(row.getCode(), "角色编码"),
                SystemExcelAssembler.required(row.getName(), "角色名称"),
                SystemExcelAssembler.required(row.getDeptPath(), "所属部门路径"),
                SystemExcelAssembler.parseEnum(row.getLevel(), RoleLevel.class, RoleLevel.USER, "角色等级"),
                SystemExcelAssembler.parseStringList(row.getPermissions()),
                SystemExcelAssembler.trimToNull(row.getIcon()),
                SystemExcelAssembler.trimToNull(row.getColor()),
                Boolean.TRUE.equals(SystemExcelAssembler.parseNullableBoolean(row.getSystemRole(), "系统角色")),
                SystemExcelAssembler.parseEnum(row.getStatus(), RoleStatus.class, RoleStatus.ACTIVE, "角色状态")
        );
    }

    private static TemplateUserImportCmd toUserCmd(UserExcelRow row) {
        return new TemplateUserImportCmd(
                SystemExcelAssembler.parseLong(row.getId(), "用户ID"),
                SystemExcelAssembler.required(row.getUsername(), "用户名"),
                SystemExcelAssembler.trimToNull(row.getNickname()),
                SystemExcelAssembler.trimToNull(row.getEmail()),
                SystemExcelAssembler.trimToNull(row.getPhone()),
                SystemExcelAssembler.trimToNull(row.getQq()),
                SystemExcelAssembler.parseNullableBoolean(row.getEmailVerified(), "邮箱已验证"),
                SystemExcelAssembler.parseEnum(row.getStatus(), UserStatus.class, UserStatus.ACTIVE, "用户状态"),
                splitPaths(row.getDeptPaths()),
                SystemExcelAssembler.trimToNull(row.getDefaultDeptPath()),
                SystemExcelAssembler.parseStringList(row.getRoleCodes())
        );
    }

    private static TemplateMenuImportCmd toMenuCmd(MenuExcelRow row) {
        return new TemplateMenuImportCmd(
                SystemExcelAssembler.parseLong(row.getId(), "菜单ID"),
                SystemExcelAssembler.required(row.getCode(), "菜单编码"),
                SystemExcelAssembler.required(row.getName(), "菜单名称"),
                SystemExcelAssembler.parseEnum(row.getType(), online.yudream.base.domain.system.menu.enumerate.MenuNodeType.class,
                        online.yudream.base.domain.system.menu.enumerate.MenuNodeType.MENU, "菜单类型"),
                SystemExcelAssembler.trimToNull(row.getParentCode()),
                SystemExcelAssembler.trimToNull(row.getModule()),
                SystemExcelAssembler.trimToNull(row.getIcon()),
                SystemExcelAssembler.trimToNull(row.getPath()),
                SystemExcelAssembler.trimToNull(row.getComponent()),
                SystemExcelAssembler.trimToNull(row.getLink()),
                row.getSort(),
                SystemExcelAssembler.parseNullableBoolean(row.getVisible(), "是否显示"),
                SystemExcelAssembler.trimToNull(row.getPermission()),
                SystemExcelAssembler.parseEnum(row.getStatus(), MenuStatus.class, MenuStatus.ACTIVE, "菜单状态")
        );
    }

    private static DeptExcelRow toRow(TemplateDeptDTO dto) {
        DeptExcelRow row = new DeptExcelRow();
        row.setId(dto.sourceId());
        row.setPath(dto.path());
        row.setName(dto.name());
        row.setParentPath(dto.parentPath());
        row.setLeaderUsername(dto.leaderUsername());
        row.setPhone(dto.phone());
        row.setSortOrder(dto.sortOrder());
        row.setDescription(dto.description());
        row.setColor(dto.color());
        row.setSystemDept(dto.systemDept() ? "是" : "否");
        row.setStatus(dto.status() == null ? null : dto.status().name());
        return row;
    }

    private static RoleExcelRow toRow(TemplateRoleDTO dto) {
        RoleExcelRow row = new RoleExcelRow();
        row.setId(dto.sourceId());
        row.setCode(dto.code());
        row.setName(dto.name());
        row.setDeptPath(dto.deptPath());
        row.setLevel(dto.level() == null ? null : dto.level().name());
        row.setPermissions(String.join(",", safe(dto.permissions())));
        row.setIcon(dto.icon());
        row.setColor(dto.color());
        row.setSystemRole(dto.systemRole() ? "是" : "否");
        row.setStatus(dto.status() == null ? null : dto.status().name());
        return row;
    }

    private static UserExcelRow toRow(TemplateUserDTO dto) {
        UserExcelRow row = new UserExcelRow();
        row.setId(dto.sourceId());
        row.setUsername(dto.username());
        row.setNickname(dto.nickname());
        row.setEmail(dto.email());
        row.setPhone(dto.phone());
        row.setQq(dto.qq());
        row.setEmailVerified(dto.emailVerified() ? "是" : "否");
        row.setDeptPaths(String.join(";", safe(dto.deptPaths())));
        row.setDefaultDeptPath(dto.defaultDeptPath());
        row.setRoleCodes(String.join(",", safe(dto.roleCodes())));
        row.setStatus(dto.status() == null ? null : dto.status().name());
        return row;
    }

    private static MenuExcelRow toRow(TemplateMenuDTO dto) {
        MenuExcelRow row = new MenuExcelRow();
        row.setId(dto.sourceId());
        row.setCode(dto.code());
        row.setName(dto.name());
        row.setType(dto.type() == null ? null : dto.type().name());
        row.setParentCode(dto.parentCode());
        row.setModule(dto.module());
        row.setIcon(dto.icon());
        row.setPath(dto.path());
        row.setComponent(dto.component());
        row.setLink(dto.link());
        row.setSort(dto.sort());
        row.setVisible(dto.visible() == null || dto.visible() ? "是" : "否");
        row.setPermission(dto.permission());
        row.setStatus(dto.status() == null ? null : dto.status().name());
        return row;
    }

    private static List<String> splitPaths(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split("[;；\\n]+"))
                .map(String::trim).filter(item -> !item.isBlank()).distinct().toList();
    }

    private static String issueText(SystemTemplateIssueDTO issue) {
        if (issue == null) return "导入提示";
        String location = issue.row() > 0 ? issue.sheet() + "第 " + issue.row() + " 行" : issue.sheet();
        return location + "：" + issue.message();
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
