package online.yudream.base.interfaces.system.excel.assembler;

import online.yudream.base.application.system.menu.cmd.MenuCreateCmd;
import online.yudream.base.application.system.menu.dto.MenuManageDTO;
import online.yudream.base.application.system.user.cmd.DeptCreateCmd;
import online.yudream.base.application.system.user.cmd.RoleCreateCmd;
import online.yudream.base.application.system.user.cmd.UserCreateCmd;
import online.yudream.base.application.system.user.cmd.UserDeptAssignCmd;
import online.yudream.base.application.system.user.dto.DeptManageDTO;
import online.yudream.base.application.system.user.dto.RoleManageDTO;
import online.yudream.base.application.system.user.dto.UserManageDTO;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.monitor.dto.ApiLogDTO;
import online.yudream.base.domain.system.monitor.dto.LoginLogDTO;
import online.yudream.base.domain.system.monitor.dto.OnlineUserDTO;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.interfaces.system.excel.row.ApiLogExcelRow;
import online.yudream.base.interfaces.system.excel.row.DeptExcelRow;
import online.yudream.base.interfaces.system.excel.row.LoginLogExcelRow;
import online.yudream.base.interfaces.system.excel.row.MenuExcelRow;
import online.yudream.base.interfaces.system.excel.row.OnlineUserExcelRow;
import online.yudream.base.interfaces.system.excel.row.RoleExcelRow;
import online.yudream.base.interfaces.system.excel.row.UserExcelRow;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SystemExcelAssembler {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SystemExcelAssembler() {
    }

    public static UserExcelRow userTemplateRow() {
        UserExcelRow row = new UserExcelRow();
        row.setUsername("zhangsan");
        row.setNickname("张三");
        row.setEmail("zhangsan@example.com");
        row.setPassword("Yd@123456");
        row.setEmailVerified("是");
        row.setDeptIds("1,3");
        row.setDefaultDeptId(1L);
        row.setRoleIds("1001,1002");
        return row;
    }

    public static RoleExcelRow roleTemplateRow() {
        RoleExcelRow row = new RoleExcelRow();
        row.setName("项目管理员");
        row.setCode("project_admin");
        row.setDeptId(1L);
        row.setLevel("ADMIN");
        row.setPermissions("system:user,system:user:create");
        return row;
    }

    public static DeptExcelRow deptTemplateRow() {
        DeptExcelRow row = new DeptExcelRow();
        row.setName("研发部");
        row.setParentId(1L);
        row.setPhone("13800000000");
        row.setSortOrder(10);
        row.setDescription("部门描述");
        return row;
    }

    public static MenuExcelRow menuTemplateRow() {
        MenuExcelRow row = new MenuExcelRow();
        row.setCode("system:demo");
        row.setName("示例菜单");
        row.setType("MENU");
        row.setParentCode("system");
        row.setPath("/system/demo");
        row.setComponent("system/demo/index.vue");
        row.setIcon("i-ri:file-list-line");
        row.setSort(10);
        row.setPermission("system:demo");
        return row;
    }

    public static UserExcelRow toUserRow(UserManageDTO dto) {
        UserExcelRow row = new UserExcelRow();
        row.setId(dto.getId());
        row.setUsername(dto.getUsername());
        row.setNickname(dto.getNickname());
        row.setEmail(dto.getEmail());
        row.setPhone(dto.getPhone());
        row.setQq(dto.getQq());
        row.setEmailVerified(yesNo(dto.isEmailVerified()));
        row.setDeptIds(join(dto.getDeptIds()));
        row.setDeptNames(join(dto.getDeptNames()));
        row.setDefaultDeptId(dto.getDefaultDeptId());
        row.setRoleIds(join(dto.getRoleIds()));
        row.setRoleNames(join(dto.getRoleNames()));
        row.setStatus(enumName(dto.getStatus()));
        row.setCreateTime(format(dto.getCreateTime()));
        return row;
    }

    public static UserCreateCmd toUserCreateCmd(UserExcelRow row) {
        UserCreateCmd cmd = new UserCreateCmd();
        cmd.setUsername(required(row.getUsername(), "用户名"));
        cmd.setNickname(row.getNickname());
        cmd.setEmail(required(row.getEmail(), "邮箱"));
        cmd.setPhone(row.getPhone());
        cmd.setQq(row.getQq());
        cmd.setPassword(required(row.getPassword(), "初始密码"));
        cmd.setEmailVerified(parseBoolean(row.getEmailVerified()));
        cmd.setRoleIds(parseLongList(row.getRoleIds()));
        List<Long> deptIds = parseLongList(row.getDeptIds());
        Long defaultDeptId = row.getDefaultDeptId() == null && !deptIds.isEmpty() ? deptIds.get(0) : row.getDefaultDeptId();
        cmd.setDepts(deptIds.stream().map(id -> {
            UserDeptAssignCmd dept = new UserDeptAssignCmd();
            dept.setDeptId(id);
            dept.setDefaultDept(Objects.equals(id, defaultDeptId));
            return dept;
        }).toList());
        return cmd;
    }

    public static RoleExcelRow toRoleRow(RoleManageDTO dto) {
        RoleExcelRow row = new RoleExcelRow();
        row.setId(dto.getId());
        row.setName(dto.getName());
        row.setCode(dto.getCode());
        row.setDeptId(dto.getDeptId());
        row.setDeptName(dto.getDeptName());
        row.setLevel(enumName(dto.getLevel()));
        row.setPermissions(join(dto.getPermissions()));
        row.setSystemRole(yesNo(dto.isSystemRole()));
        row.setStatus(enumName(dto.getStatus()));
        row.setCreateTime(format(dto.getCreateTime()));
        return row;
    }

    public static RoleCreateCmd toRoleCreateCmd(RoleExcelRow row) {
        RoleCreateCmd cmd = new RoleCreateCmd();
        cmd.setName(required(row.getName(), "角色名称"));
        cmd.setCode(required(row.getCode(), "角色编码"));
        cmd.setDeptId(row.getDeptId());
        cmd.setLevel(StringUtils.hasText(row.getLevel()) ? RoleLevel.valueOf(row.getLevel().trim()) : RoleLevel.USER);
        cmd.setPermissions(parseStringList(row.getPermissions()));
        return cmd;
    }

    public static DeptExcelRow toDeptRow(DeptManageDTO dto) {
        DeptExcelRow row = new DeptExcelRow();
        row.setId(dto.getId());
        row.setName(dto.getName());
        row.setParentId(dto.getParentId());
        row.setLeaderId(dto.getLeaderId());
        row.setLeaderName(dto.getLeaderName());
        row.setPhone(dto.getPhone());
        row.setSortOrder(dto.getSortOrder());
        row.setDescription(dto.getDescription());
        row.setSystemDept(yesNo(dto.isSystemDept()));
        row.setStatus(enumName(dto.getStatus()));
        row.setCreateTime(format(dto.getCreateTime()));
        return row;
    }

    public static DeptCreateCmd toDeptCreateCmd(DeptExcelRow row) {
        DeptCreateCmd cmd = new DeptCreateCmd();
        cmd.setName(required(row.getName(), "部门名称"));
        cmd.setParentId(row.getParentId());
        cmd.setLeaderId(row.getLeaderId());
        cmd.setPhone(row.getPhone());
        cmd.setSortOrder(row.getSortOrder());
        cmd.setDescription(row.getDescription());
        return cmd;
    }

    public static MenuExcelRow toMenuRow(MenuManageDTO dto) {
        MenuExcelRow row = new MenuExcelRow();
        row.setCode(dto.getCode());
        row.setName(dto.getName());
        row.setType(enumName(dto.getType()));
        row.setParentCode(dto.getParentCode());
        row.setModule(dto.getModule());
        row.setIcon(dto.getIcon());
        row.setPath(dto.getPath());
        row.setComponent(dto.getComponent());
        row.setLink(dto.getLink());
        row.setSort(dto.getSort());
        row.setPermission(dto.getPermission());
        row.setStatus(enumName(dto.getStatus()));
        return row;
    }

    public static MenuCreateCmd toMenuCreateCmd(MenuExcelRow row) {
        MenuCreateCmd cmd = new MenuCreateCmd();
        cmd.setCode(required(row.getCode(), "菜单编码"));
        cmd.setName(required(row.getName(), "菜单名称"));
        cmd.setType(StringUtils.hasText(row.getType()) ? MenuNodeType.valueOf(row.getType().trim()) : MenuNodeType.MENU);
        cmd.setParentCode(row.getParentCode());
        cmd.setModule(row.getModule());
        cmd.setIcon(row.getIcon());
        cmd.setPath(row.getPath());
        cmd.setComponent(row.getComponent());
        cmd.setLink(row.getLink());
        cmd.setSort(row.getSort());
        cmd.setPermission(row.getPermission());
        return cmd;
    }

    public static ApiLogExcelRow toApiLogRow(ApiLogDTO dto) {
        ApiLogExcelRow row = new ApiLogExcelRow();
        row.setId(dto.getId());
        row.setMethod(dto.getMethod());
        row.setPath(dto.getPath());
        row.setStatus(dto.getStatus());
        row.setSuccess(yesNo(Boolean.TRUE.equals(dto.getSuccess())));
        row.setCostMs(dto.getCostMs());
        row.setLoginId(dto.getLoginId());
        row.setUsername(dto.getUsername());
        row.setNickname(dto.getNickname());
        row.setIp(dto.getIp());
        row.setErrorMessage(dto.getErrorMessage());
        row.setCreateTime(format(dto.getCreateTime()));
        return row;
    }

    public static LoginLogExcelRow toLoginLogRow(LoginLogDTO dto) {
        LoginLogExcelRow row = new LoginLogExcelRow();
        row.setId(dto.getId());
        row.setUsername(dto.getUsername());
        row.setUserId(dto.getUserId());
        row.setSuccess(yesNo(Boolean.TRUE.equals(dto.getSuccess())));
        row.setMessage(dto.getMessage());
        row.setIp(dto.getIp());
        row.setCreateTime(format(dto.getCreateTime()));
        return row;
    }

    public static OnlineUserExcelRow toOnlineUserRow(OnlineUserDTO dto) {
        OnlineUserExcelRow row = new OnlineUserExcelRow();
        row.setUserId(dto.getUserId());
        row.setUsername(dto.getUsername());
        row.setNickname(dto.getNickname());
        row.setEmail(dto.getEmail());
        row.setDevice(dto.getDevice());
        row.setTimeout(dto.getTimeout());
        row.setActiveTimeout(dto.getActiveTimeout());
        row.setToken(dto.getToken());
        return row;
    }

    public static List<DeptManageDTO> flattenDepts(List<DeptManageDTO> tree) {
        if (tree == null || tree.isEmpty()) {
            return Collections.emptyList();
        }
        List<DeptManageDTO> result = new ArrayList<>();
        for (DeptManageDTO item : tree) {
            result.add(item);
            result.addAll(flattenDepts(item.getChildren()));
        }
        return result;
    }

    public static List<MenuManageDTO> flattenMenus(List<MenuManageDTO> tree) {
        if (tree == null || tree.isEmpty()) {
            return Collections.emptyList();
        }
        List<MenuManageDTO> result = new ArrayList<>();
        for (MenuManageDTO item : tree) {
            result.add(item);
            result.addAll(flattenMenus(item.getChildren()));
        }
        return result;
    }

    private static String required(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value.trim();
    }

    private static boolean parseBoolean(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String text = value.trim();
        return "是".equals(text) || "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private static List<Long> parseLongList(String value) {
        return parseStringList(value).stream().map(Long::valueOf).toList();
    }

    private static List<String> parseStringList(String value) {
        if (!StringUtils.hasText(value)) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.split("[,，;；\\s]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private static String join(List<?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String format(LocalDateTime value) {
        return value == null ? null : DATE_TIME_FORMATTER.format(value);
    }
}
