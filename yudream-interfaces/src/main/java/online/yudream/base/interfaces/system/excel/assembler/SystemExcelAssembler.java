package online.yudream.base.interfaces.system.excel.assembler;

import online.yudream.base.application.platform.agent.dto.AgentTraceSummaryDTO;
import online.yudream.base.application.system.menu.cmd.MenuCreateCmd;
import online.yudream.base.application.system.menu.cmd.MenuImportCmd;
import online.yudream.base.application.system.menu.dto.MenuManageDTO;
import online.yudream.base.application.system.user.cmd.DeptCreateCmd;
import online.yudream.base.application.system.user.cmd.TemplateDeptUpsertCmd;
import online.yudream.base.application.system.user.cmd.TemplateRoleUpsertCmd;
import online.yudream.base.application.system.user.cmd.TemplateUserUpsertCmd;
import online.yudream.base.application.system.user.cmd.RoleCreateCmd;
import online.yudream.base.application.system.user.cmd.UserCreateCmd;
import online.yudream.base.application.system.user.cmd.UserDeptAssignCmd;
import online.yudream.base.application.system.user.dto.DeptManageDTO;
import online.yudream.base.application.system.user.dto.RoleManageDTO;
import online.yudream.base.application.system.user.dto.UserManageDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.monitor.dto.ApiLogDTO;
import online.yudream.base.domain.system.monitor.dto.LoginLogDTO;
import online.yudream.base.domain.system.monitor.dto.OnlineUserDTO;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.interfaces.system.excel.row.AgentTraceExcelRow;
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
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class SystemExcelAssembler {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SystemExcelAssembler() {
    }

    /** 空模板不再预填可被误提交的真实业务示例数据。 */
    public static UserExcelRow userTemplateRow() {
        return new UserExcelRow();
    }

    public static RoleExcelRow roleTemplateRow() {
        return new RoleExcelRow();
    }

    public static DeptExcelRow deptTemplateRow() {
        return new DeptExcelRow();
    }

    public static MenuExcelRow menuTemplateRow() {
        return new MenuExcelRow();
    }

    public static UserExcelRow toUserRow(UserManageDTO dto) {
        UserExcelRow row = new UserExcelRow();
        row.setId(id(dto.getId()));
        row.setUsername(dto.getUsername());
        row.setNickname(dto.getNickname());
        row.setEmail(dto.getEmail());
        row.setPhone(dto.getPhone());
        row.setQq(dto.getQq());
        // 密码、头像、消息身份、扩展字段均不属于可移植模板。
        row.setEmailVerified(yesNo(dto.isEmailVerified()));
        row.setDeptIds(join(dto.getDeptIds()));
        row.setDeptNames(join(dto.getDeptNames()));
        row.setDefaultDeptId(id(dto.getDefaultDeptId()));
        row.setRoleIds(join(dto.getRoleIds()));
        row.setRoleNames(join(dto.getRoleNames()));
        row.setStatus(enumName(dto.getStatus()));
        row.setCreateTime(format(dto.getCreateTime()));
        return row;
    }

    public static UserCreateCmd toUserCreateCmd(UserExcelRow row) {
        UserCreateCmd cmd = new UserCreateCmd();
        cmd.setUsername(required(row.getUsername(), "用户名"));
        cmd.setNickname(trimToNull(row.getNickname()));
        cmd.setEmail(required(row.getEmail(), "邮箱"));
        cmd.setPhone(trimToNull(row.getPhone()));
        cmd.setQq(trimToNull(row.getQq()));
        cmd.setPassword(required(row.getPassword(), "初始密码"));
        cmd.setEmailVerified(parseBoolean(row.getEmailVerified()));
        cmd.setRoleIds(parseLongList(row.getRoleIds(), "角色ID"));
        List<Long> deptIds = parseLongList(row.getDeptIds(), "部门ID");
        Long defaultDeptId = parseLong(row.getDefaultDeptId(), "默认部门ID");
        if (defaultDeptId == null && !deptIds.isEmpty()) {
            defaultDeptId = deptIds.getFirst();
        }
        Long finalDefaultDeptId = defaultDeptId;
        cmd.setDepts(deptIds.stream().map(value -> {
            UserDeptAssignCmd dept = new UserDeptAssignCmd();
            dept.setDeptId(value);
            dept.setDefaultDept(Objects.equals(value, finalDefaultDeptId));
            return dept;
        }).toList());
        return cmd;
    }

    public static TemplateUserUpsertCmd toUserImportCmd(UserExcelRow row) {
        TemplateUserUpsertCmd cmd = new TemplateUserUpsertCmd();
        cmd.setSourceId(trimToNull(row.getId()));
        cmd.setUsername(required(row.getUsername(), "用户名"));
        cmd.setNickname(trimToNull(row.getNickname()));
        cmd.setEmail(trimToNull(row.getEmail()));
        cmd.setPhone(trimToNull(row.getPhone()));
        cmd.setQq(trimToNull(row.getQq()));
        cmd.setEmailVerified(parseNullableBoolean(row.getEmailVerified(), "邮箱已验证"));
        cmd.setStatus(parseEnum(row.getStatus(), online.yudream.base.domain.system.user.enumerate.UserStatus.class,
                null, "用户状态"));
        cmd.setDeptIds(parseLongList(row.getDeptIds(), "部门ID"));
        cmd.setDefaultDeptId(parseLong(row.getDefaultDeptId(), "默认部门ID"));
        cmd.setRoleIds(parseLongList(row.getRoleIds(), "角色ID"));
        return cmd;
    }

    public static RoleExcelRow toRoleRow(RoleManageDTO dto) {
        RoleExcelRow row = new RoleExcelRow();
        row.setId(id(dto.getId()));
        row.setName(dto.getName());
        row.setCode(dto.getCode());
        row.setDeptId(id(dto.getDeptId()));
        row.setDeptName(dto.getDeptName());
        row.setLevel(enumName(dto.getLevel()));
        row.setPermissions(join(dto.getPermissions()));
        row.setIcon(dto.getIcon());
        row.setColor(dto.getColor());
        row.setSystemRole(yesNo(dto.isSystemRole()));
        row.setStatus(enumName(dto.getStatus()));
        row.setCreateTime(format(dto.getCreateTime()));
        return row;
    }

    public static RoleCreateCmd toRoleCreateCmd(RoleExcelRow row) {
        RoleCreateCmd cmd = new RoleCreateCmd();
        cmd.setName(required(row.getName(), "角色名称"));
        cmd.setCode(required(row.getCode(), "角色编码"));
        cmd.setDeptId(requiredLong(row.getDeptId(), "部门ID"));
        cmd.setLevel(parseEnum(row.getLevel(), RoleLevel.class, RoleLevel.USER, "角色等级"));
        cmd.setIcon(trimToNull(row.getIcon()));
        cmd.setColor(trimToNull(row.getColor()));
        cmd.setPermissions(parseStringList(row.getPermissions()));
        return cmd;
    }

    public static TemplateRoleUpsertCmd toRoleImportCmd(RoleExcelRow row) {
        TemplateRoleUpsertCmd cmd = new TemplateRoleUpsertCmd();
        cmd.setSourceId(parseLong(row.getId(), "角色ID"));
        cmd.setCode(required(row.getCode(), "角色编码"));
        cmd.setName(required(row.getName(), "角色名称"));
        cmd.setDeptId(parseLong(row.getDeptId(), "部门ID"));
        cmd.setLevel(parseEnum(row.getLevel(), RoleLevel.class, null, "角色等级"));
        cmd.setPermissions(parseStringList(row.getPermissions()));
        cmd.setIcon(trimToNull(row.getIcon()));
        cmd.setColor(trimToNull(row.getColor()));
        cmd.setStatus(parseEnum(row.getStatus(), online.yudream.base.domain.system.user.enumerate.RoleStatus.class,
                null, "角色状态"));
        return cmd;
    }

    public static DeptExcelRow toDeptRow(DeptManageDTO dto) {
        DeptExcelRow row = new DeptExcelRow();
        row.setId(id(dto.getId()));
        row.setName(dto.getName());
        row.setParentId(id(dto.getParentId()));
        row.setLeaderId(id(dto.getLeaderId()));
        row.setLeaderName(dto.getLeaderName());
        row.setPhone(dto.getPhone());
        row.setSortOrder(dto.getSortOrder());
        row.setDescription(dto.getDescription());
        row.setColor(dto.getColor());
        row.setSystemDept(yesNo(dto.isSystemDept()));
        row.setStatus(enumName(dto.getStatus()));
        row.setCreateTime(format(dto.getCreateTime()));
        return row;
    }

    public static DeptCreateCmd toDeptCreateCmd(DeptExcelRow row) {
        DeptCreateCmd cmd = new DeptCreateCmd();
        cmd.setName(required(row.getName(), "部门名称"));
        cmd.setParentId(parseLong(row.getParentId(), "上级部门ID"));
        cmd.setLeaderId(parseLong(row.getLeaderId(), "负责人ID"));
        cmd.setPhone(trimToNull(row.getPhone()));
        cmd.setSortOrder(row.getSortOrder());
        cmd.setDescription(trimToNull(row.getDescription()));
        cmd.setColor(trimToNull(row.getColor()));
        return cmd;
    }

    public static TemplateDeptUpsertCmd toDeptImportCmd(DeptExcelRow row) {
        TemplateDeptUpsertCmd cmd = new TemplateDeptUpsertCmd();
        cmd.setSourceId(parseLong(row.getId(), "部门ID"));
        cmd.setName(required(row.getName(), "部门名称"));
        cmd.setParentSpecified(true);
        cmd.setParentId(parseLong(row.getParentId(), "上级部门ID"));
        cmd.setLeaderSpecified(true);
        cmd.setLeaderId(parseLong(row.getLeaderId(), "负责人ID"));
        cmd.setPhone(trimToNull(row.getPhone()));
        cmd.setSortOrder(row.getSortOrder());
        cmd.setDescription(trimToNull(row.getDescription()));
        cmd.setColor(trimToNull(row.getColor()));
        cmd.setStatus(parseEnum(row.getStatus(), online.yudream.base.domain.system.user.enumerate.DeptStatus.class,
                null, "部门状态"));
        return cmd;
    }

    public static MenuExcelRow toMenuRow(MenuManageDTO dto) {
        MenuExcelRow row = new MenuExcelRow();
        row.setId(id(dto.getId()));
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
        row.setVisible(yesNo(dto.getVisible() == null || dto.getVisible()));
        row.setPermission(dto.getPermission());
        row.setStatus(enumName(dto.getStatus()));
        return row;
    }

    public static MenuCreateCmd toMenuCreateCmd(MenuExcelRow row) {
        MenuCreateCmd cmd = new MenuCreateCmd();
        cmd.setCode(required(row.getCode(), "菜单编码"));
        cmd.setName(required(row.getName(), "菜单名称"));
        cmd.setType(parseEnum(row.getType(), MenuNodeType.class, MenuNodeType.MENU, "菜单类型"));
        cmd.setParentCode(trimToNull(row.getParentCode()));
        cmd.setModule(trimToNull(row.getModule()));
        cmd.setIcon(trimToNull(row.getIcon()));
        cmd.setPath(trimToNull(row.getPath()));
        cmd.setComponent(trimToNull(row.getComponent()));
        cmd.setLink(trimToNull(row.getLink()));
        cmd.setSort(row.getSort());
        cmd.setVisible(parseNullableBoolean(row.getVisible(), "是否显示"));
        cmd.setPermission(trimToNull(row.getPermission()));
        return cmd;
    }

    public static MenuImportCmd toMenuImportCmd(MenuExcelRow row) {
        MenuImportCmd cmd = new MenuImportCmd();
        cmd.setSourceId(parseLong(row.getId(), "菜单ID"));
        cmd.setCode(required(row.getCode(), "菜单编码"));
        cmd.setName(required(row.getName(), "菜单名称"));
        cmd.setType(parseEnum(row.getType(), MenuNodeType.class, MenuNodeType.MENU, "菜单类型"));
        cmd.setParentCode(trimToNull(row.getParentCode()));
        cmd.setModule(trimToNull(row.getModule()));
        cmd.setIcon(trimToNull(row.getIcon()));
        cmd.setPath(trimToNull(row.getPath()));
        cmd.setComponent(trimToNull(row.getComponent()));
        cmd.setLink(trimToNull(row.getLink()));
        cmd.setSort(row.getSort());
        cmd.setVisible(parseNullableBoolean(row.getVisible(), "是否显示"));
        cmd.setPermission(trimToNull(row.getPermission()));
        cmd.setStatus(parseEnum(row.getStatus(), MenuStatus.class, MenuStatus.ACTIVE, "菜单状态"));
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

    public static AgentTraceExcelRow toAgentTraceRow(AgentTraceSummaryDTO dto) {
        AgentTraceExcelRow row = new AgentTraceExcelRow();
        row.setTraceId(dto.getTraceId());
        row.setStatus(enumName(dto.getStatus()));
        row.setSource(enumName(dto.getSource()));
        row.setAgentName(dto.getAgentName());
        row.setAgentCode(dto.getAgentCode());
        row.setOwnerPluginCode(dto.getOwnerPluginCode());
        row.setStepCount(dto.getStepCount());
        row.setDurationMs(dto.getDurationMs());
        row.setInput(dto.getInput());
        row.setError(dto.getError());
        row.setStartTime(format(dto.getStartTime()));
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

    public static String required(String value, String name) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BizException(name + "不能为空");
        }
        return normalized;
    }

    public static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public static Long requiredLong(String value, String name) {
        Long parsed = parseLong(value, name);
        if (parsed == null) {
            throw new BizException(name + "不能为空");
        }
        return parsed;
    }

    public static Long parseLong(String value, String name) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Long.valueOf(normalized);
        }
        catch (NumberFormatException e) {
            throw new BizException(name + "必须是完整整数标识");
        }
    }

    public static boolean parseBoolean(String value) {
        Boolean parsed = parseNullableBoolean(value, "布尔值");
        return Boolean.TRUE.equals(parsed);
    }

    public static Boolean parseNullableBoolean(String value, String name) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        if ("是".equals(text) || "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text)) {
            return true;
        }
        if ("否".equals(text) || "false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text)) {
            return false;
        }
        throw new BizException(name + "仅支持 是/否 或 true/false");
    }

    public static <E extends Enum<E>> E parseEnum(String value, Class<E> type, E defaultValue, String name) {
        String text = trimToNull(value);
        if (text == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, text.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e) {
            throw new BizException(name + "值无效：" + text);
        }
    }

    public static List<Long> parseLongList(String value, String name) {
        return parseStringList(value).stream().map(item -> parseLong(item, name)).toList();
    }

    public static List<String> parseStringList(String value) {
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

    private static String id(Long value) {
        return value == null ? null : String.valueOf(value);
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
