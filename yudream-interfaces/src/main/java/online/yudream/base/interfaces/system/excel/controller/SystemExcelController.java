package online.yudream.base.interfaces.system.excel.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.menu.query.MenuTreeQuery;
import online.yudream.base.application.system.menu.service.MenuAppService;
import online.yudream.base.application.system.monitor.service.SystemMonitorAppService;
import online.yudream.base.application.system.user.query.DeptTreeQuery;
import online.yudream.base.application.system.user.query.RolePageQuery;
import online.yudream.base.application.system.user.query.UserPageQuery;
import online.yudream.base.application.system.user.service.DeptManageAppService;
import online.yudream.base.application.system.user.service.RoleManageAppService;
import online.yudream.base.application.system.user.service.UserManageAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.monitor.dto.ApiLogDTO;
import online.yudream.base.domain.system.monitor.dto.LoginLogDTO;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.excel.assembler.SystemExcelAssembler;
import online.yudream.base.interfaces.system.excel.res.ExcelImportResultRes;
import online.yudream.base.interfaces.system.excel.row.ApiLogExcelRow;
import online.yudream.base.interfaces.system.excel.row.DeptExcelRow;
import online.yudream.base.interfaces.system.excel.row.LoginLogExcelRow;
import online.yudream.base.interfaces.system.excel.row.MenuExcelRow;
import online.yudream.base.interfaces.system.excel.row.OnlineUserExcelRow;
import online.yudream.base.interfaces.system.excel.row.RoleExcelRow;
import online.yudream.base.interfaces.system.excel.row.UserExcelRow;
import online.yudream.base.interfaces.system.excel.support.ExcelHttpSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/system/excel")
@RequiredArgsConstructor
public class SystemExcelController {

    private static final int EXPORT_LIMIT = 10_000;

    private final UserManageAppService userManageAppService;
    private final RoleManageAppService roleManageAppService;
    private final DeptManageAppService deptManageAppService;
    private final MenuAppService menuAppService;
    private final SystemMonitorAppService systemMonitorAppService;

    @GetMapping("/users/export")
    @PermissionRegister(code = "system:user:export", name = "导出用户", module = "系统管理", desc = "导出用户 Excel")
    public void exportUsers(UserPageQuery query, HttpServletResponse response) throws IOException {
        query.setPage(1);
        query.setSize(EXPORT_LIMIT);
        List<UserExcelRow> rows = userManageAppService.page(query).getRecords().stream()
                .map(SystemExcelAssembler::toUserRow)
                .toList();
        ExcelHttpSupport.write(response, "用户管理", "用户", UserExcelRow.class, rows);
    }

    @GetMapping("/users/template")
    @PermissionRegister(code = "system:user:import", name = "导入用户", module = "系统管理", desc = "导入用户 Excel")
    public void userTemplate(HttpServletResponse response) throws IOException {
        ExcelHttpSupport.write(response, "用户导入模板", "用户", UserExcelRow.class, List.of(SystemExcelAssembler.userTemplateRow()));
    }

    @PostMapping("/users/import")
    @PermissionRegister(code = "system:user:import", name = "导入用户", module = "系统管理", desc = "导入用户 Excel")
    public Result<ExcelImportResultRes> importUsers(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(ExcelHttpSupport.importRows(file, UserExcelRow.class,
                row -> userManageAppService.create(SystemExcelAssembler.toUserCreateCmd(row))));
    }

    @GetMapping("/roles/export")
    @PermissionRegister(code = "system:role:export", name = "导出角色", module = "系统管理", desc = "导出角色 Excel")
    public void exportRoles(RolePageQuery query, HttpServletResponse response) throws IOException {
        query.setPage(1);
        query.setSize(EXPORT_LIMIT);
        List<RoleExcelRow> rows = roleManageAppService.page(query).getRecords().stream()
                .map(SystemExcelAssembler::toRoleRow)
                .toList();
        ExcelHttpSupport.write(response, "角色管理", "角色", RoleExcelRow.class, rows);
    }

    @GetMapping("/roles/template")
    @PermissionRegister(code = "system:role:import", name = "导入角色", module = "系统管理", desc = "导入角色 Excel")
    public void roleTemplate(HttpServletResponse response) throws IOException {
        ExcelHttpSupport.write(response, "角色导入模板", "角色", RoleExcelRow.class, List.of(SystemExcelAssembler.roleTemplateRow()));
    }

    @PostMapping("/roles/import")
    @PermissionRegister(code = "system:role:import", name = "导入角色", module = "系统管理", desc = "导入角色 Excel")
    public Result<ExcelImportResultRes> importRoles(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(ExcelHttpSupport.importRows(file, RoleExcelRow.class,
                row -> roleManageAppService.create(SystemExcelAssembler.toRoleCreateCmd(row))));
    }

    @GetMapping("/depts/export")
    @PermissionRegister(code = "system:dept:export", name = "导出部门", module = "系统管理", desc = "导出部门 Excel")
    public void exportDepts(DeptTreeQuery query, HttpServletResponse response) throws IOException {
        List<DeptExcelRow> rows = SystemExcelAssembler.flattenDepts(deptManageAppService.tree(query)).stream()
                .map(SystemExcelAssembler::toDeptRow)
                .toList();
        ExcelHttpSupport.write(response, "部门管理", "部门", DeptExcelRow.class, rows);
    }

    @GetMapping("/depts/template")
    @PermissionRegister(code = "system:dept:import", name = "导入部门", module = "系统管理", desc = "导入部门 Excel")
    public void deptTemplate(HttpServletResponse response) throws IOException {
        ExcelHttpSupport.write(response, "部门导入模板", "部门", DeptExcelRow.class, List.of(SystemExcelAssembler.deptTemplateRow()));
    }

    @PostMapping("/depts/import")
    @PermissionRegister(code = "system:dept:import", name = "导入部门", module = "系统管理", desc = "导入部门 Excel")
    public Result<ExcelImportResultRes> importDepts(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(ExcelHttpSupport.importRows(file, DeptExcelRow.class,
                row -> deptManageAppService.create(SystemExcelAssembler.toDeptCreateCmd(row))));
    }

    @GetMapping("/menus/export")
    @PermissionRegister(code = "system:menu:export", name = "导出菜单", module = "系统管理", desc = "导出菜单 Excel")
    public void exportMenus(MenuTreeQuery query, HttpServletResponse response) throws IOException {
        List<MenuExcelRow> rows = SystemExcelAssembler.flattenMenus(menuAppService.tree(query)).stream()
                .map(SystemExcelAssembler::toMenuRow)
                .toList();
        ExcelHttpSupport.write(response, "菜单管理", "菜单", MenuExcelRow.class, rows);
    }

    @GetMapping("/menus/template")
    @PermissionRegister(code = "system:menu:import", name = "导入菜单", module = "系统管理", desc = "导入菜单 Excel")
    public void menuTemplate(HttpServletResponse response) throws IOException {
        ExcelHttpSupport.write(response, "菜单导入模板", "菜单", MenuExcelRow.class, List.of(SystemExcelAssembler.menuTemplateRow()));
    }

    @PostMapping("/menus/import")
    @PermissionRegister(code = "system:menu:import", name = "导入菜单", module = "系统管理", desc = "导入菜单 Excel")
    public Result<ExcelImportResultRes> importMenus(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(ExcelHttpSupport.importRows(file, MenuExcelRow.class,
                row -> menuAppService.create(SystemExcelAssembler.toMenuCreateCmd(row))));
    }

    @GetMapping("/api-logs/export")
    @PermissionRegister(code = "system:monitor:api-log:export", name = "导出接口日志", module = "系统管理", desc = "导出接口日志 Excel")
    public void exportApiLogs(@RequestParam(value = "keyword", required = false) String keyword,
                              @RequestParam(value = "success", required = false) Boolean success,
                              HttpServletResponse response) throws IOException {
        PageResult<ApiLogDTO> page = systemMonitorAppService.pageApiLogs(keyword, success, 1, EXPORT_LIMIT);
        ExcelHttpSupport.write(response, "接口日志", "接口日志", ApiLogExcelRow.class,
                page.getRecords().stream().map(SystemExcelAssembler::toApiLogRow).toList());
    }

    @GetMapping("/login-logs/export")
    @PermissionRegister(code = "system:monitor:login-log:export", name = "导出登录日志", module = "系统管理", desc = "导出登录日志 Excel")
    public void exportLoginLogs(@RequestParam(value = "keyword", required = false) String keyword,
                                @RequestParam(value = "success", required = false) Boolean success,
                                HttpServletResponse response) throws IOException {
        PageResult<LoginLogDTO> page = systemMonitorAppService.pageLoginLogs(keyword, success, 1, EXPORT_LIMIT);
        ExcelHttpSupport.write(response, "登录日志", "登录日志", LoginLogExcelRow.class,
                page.getRecords().stream().map(SystemExcelAssembler::toLoginLogRow).toList());
    }

    @GetMapping("/online-users/export")
    @PermissionRegister(code = "system:monitor:online:export", name = "导出在线用户", module = "系统管理", desc = "导出在线用户 Excel")
    public void exportOnlineUsers(@RequestParam(value = "keyword", required = false) String keyword,
                                  HttpServletResponse response) throws IOException {
        List<OnlineUserExcelRow> rows = systemMonitorAppService.onlineUsers(keyword, EXPORT_LIMIT).stream()
                .map(SystemExcelAssembler::toOnlineUserRow)
                .toList();
        ExcelHttpSupport.write(response, "在线用户", "在线用户", OnlineUserExcelRow.class, rows);
    }
}
