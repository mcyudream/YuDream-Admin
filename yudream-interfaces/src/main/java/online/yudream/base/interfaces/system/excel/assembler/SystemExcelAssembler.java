package online.yudream.base.interfaces.system.excel.assembler;

import online.yudream.base.application.platform.agent.dto.AgentTraceSummaryDTO;
import online.yudream.base.application.system.template.cmd.TemplateDeptImportCmd;
import online.yudream.base.application.system.template.cmd.TemplateMenuImportCmd;
import online.yudream.base.application.system.template.cmd.TemplateRoleImportCmd;
import online.yudream.base.application.system.template.cmd.TemplateUserImportCmd;
import online.yudream.base.application.system.template.dto.SystemTemplateImportResultDTO;
import online.yudream.base.application.system.template.dto.SystemTemplateIssueDTO;
import online.yudream.base.application.system.template.dto.TemplateDeptDTO;
import online.yudream.base.application.system.template.dto.TemplateMenuDTO;
import online.yudream.base.application.system.template.dto.TemplateRoleDTO;
import online.yudream.base.application.system.template.dto.TemplateUserDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;
import online.yudream.base.domain.system.monitor.dto.ApiLogDTO;
import online.yudream.base.domain.system.monitor.dto.LoginLogDTO;
import online.yudream.base.domain.system.monitor.dto.OnlineUserDTO;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.interfaces.system.excel.res.ExcelImportResultRes;
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
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 系统资源 Excel 边界转换器：资源页导出走稳定业务身份（部门路径/角色编码/用户名/菜单编码），
 * 导入整表转为可移植模板命令后交给应用层批处理，绝不在这里逐行落库。
 */
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

    /**
     * 资源页导出的用户行：部门按完整路径、角色按编码表示，避免跨系统 Snowflake ID 不同而失联。
     * 密码、头像、消息身份和扩展字段永不写出。
     */
    public static UserExcelRow toUserRow(TemplateUserDTO dto) {
        UserExcelRow row = new UserExcelRow();
        row.setId(dto.sourceId());
        row.setUsername(dto.username());
        row.setNickname(dto.nickname());
        row.setEmail(dto.email());
        row.setPhone(dto.phone());
        row.setQq(dto.qq());
        row.setEmailVerified(yesNo(dto.emailVerified()));
        row.setDeptPaths(join(dto.deptPaths(), ";"));
        row.setDefaultDeptPath(dto.defaultDeptPath());
        row.setRoleCodes(join(dto.roleCodes(), ","));
        row.setStatus(enumName(dto.status()));
        return row;
    }

    /** 资源页导出的角色行以所属部门完整路径作为跨系统引用。 */
    public static RoleExcelRow toRoleRow(TemplateRoleDTO dto) {
        RoleExcelRow row = new RoleExcelRow();
        row.setId(dto.sourceId());
        row.setName(dto.name());
        row.setCode(dto.code());
        row.setDeptPath(dto.deptPath());
        row.setLevel(enumName(dto.level()));
        row.setPermissions(join(dto.permissions(), ","));
        row.setIcon(dto.icon());
        row.setColor(dto.color());
        row.setSystemRole(yesNo(dto.systemRole()));
        row.setStatus(enumName(dto.status()));
        return row;
    }

    /** 资源页导出的部门行以完整路径、上级路径和负责人用户名实现跨系统回导。 */
    public static DeptExcelRow toDeptRow(TemplateDeptDTO dto) {
        DeptExcelRow row = new DeptExcelRow();
        row.setId(dto.sourceId());
        row.setName(dto.name());
        row.setPath(dto.path());
        row.setParentPath(dto.parentPath());
        row.setLeaderUsername(dto.leaderUsername());
        row.setPhone(dto.phone());
        row.setSortOrder(dto.sortOrder());
        row.setDescription(dto.description());
        row.setColor(dto.color());
        row.setSystemDept(yesNo(dto.systemDept()));
        row.setStatus(enumName(dto.status()));
        return row;
    }

    /** 资源页菜单导出：菜单编码是跨系统身份，ID 仅作同源定位。 */
    public static MenuExcelRow toMenuRow(TemplateMenuDTO dto) {
        MenuExcelRow row = new MenuExcelRow();
        row.setId(dto.sourceId());
        row.setCode(dto.code());
        row.setName(dto.name());
        row.setType(enumName(dto.type()));
        row.setParentCode(dto.parentCode());
        row.setModule(dto.module());
        row.setIcon(dto.icon());
        row.setPath(dto.path());
        row.setComponent(dto.component());
        row.setLink(dto.link());
        row.setSort(dto.sort());
        row.setVisible(yesNo(dto.visible() == null || dto.visible()));
        row.setPermission(dto.permission());
        row.setStatus(enumName(dto.status()));
        return row;
    }

    /** 将资源页用户行转为可移植模板命令；部门路径和角色编码优先于历史 ID 列。 */
    public static TemplateUserImportCmd toPortableUserImportCmd(UserExcelRow row) {
        return new TemplateUserImportCmd(
                parseLong(row.getId(), "用户ID"),
                required(row.getUsername(), "用户名"),
                trimToNull(row.getNickname()),
                trimToNull(row.getEmail()),
                trimToNull(row.getPhone()),
                trimToNull(row.getQq()),
                parseNullableBoolean(row.getEmailVerified(), "邮箱已验证"),
                parseEnum(row.getStatus(), online.yudream.base.domain.system.user.enumerate.UserStatus.class,
                        null, "用户状态"),
                splitPaths(row.getDeptPaths()),
                trimToNull(row.getDefaultDeptPath()),
                parseStringList(row.getRoleCodes())
        );
    }

    /** 将资源页角色行转为可移植模板命令；所属部门路径是跨系统权威引用。 */
    public static TemplateRoleImportCmd toPortableRoleImportCmd(RoleExcelRow row) {
        return new TemplateRoleImportCmd(
                parseLong(row.getId(), "角色ID"),
                required(row.getCode(), "角色编码"),
                required(row.getName(), "角色名称"),
                required(row.getDeptPath(), "所属部门路径"),
                parseEnum(row.getLevel(), RoleLevel.class, null, "角色等级"),
                parseStringList(row.getPermissions()),
                trimToNull(row.getIcon()),
                trimToNull(row.getColor()),
                Boolean.TRUE.equals(parseNullableBoolean(row.getSystemRole(), "系统角色")),
                parseEnum(row.getStatus(), online.yudream.base.domain.system.user.enumerate.RoleStatus.class,
                        null, "角色状态")
        );
    }

    /** 将资源页部门行转为可移植模板命令；部门路径是跨系统身份。 */
    public static TemplateDeptImportCmd toPortableDeptImportCmd(DeptExcelRow row) {
        return new TemplateDeptImportCmd(
                parseLong(row.getId(), "部门ID"),
                required(row.getPath(), "部门路径"),
                required(row.getName(), "部门名称"),
                trimToNull(row.getParentPath()),
                trimToNull(row.getLeaderUsername()),
                trimToNull(row.getPhone()),
                row.getSortOrder(),
                trimToNull(row.getDescription()),
                trimToNull(row.getColor()),
                Boolean.TRUE.equals(parseNullableBoolean(row.getSystemDept(), "系统部门")),
                parseEnum(row.getStatus(), online.yudream.base.domain.system.user.enumerate.DeptStatus.class,
                        null, "部门状态")
        );
    }

    /** 将资源页菜单行转为可移植模板命令；菜单编码是跨系统身份。 */
    public static TemplateMenuImportCmd toPortableMenuImportCmd(MenuExcelRow row) {
        return new TemplateMenuImportCmd(
                parseLong(row.getId(), "菜单ID"),
                required(row.getCode(), "菜单编码"),
                required(row.getName(), "菜单名称"),
                parseEnum(row.getType(), MenuNodeType.class, MenuNodeType.MENU, "菜单类型"),
                trimToNull(row.getParentCode()),
                trimToNull(row.getModule()),
                trimToNull(row.getIcon()),
                trimToNull(row.getPath()),
                trimToNull(row.getComponent()),
                trimToNull(row.getLink()),
                row.getSort(),
                parseNullableBoolean(row.getVisible(), "是否显示"),
                trimToNull(row.getPermission()),
                parseEnum(row.getStatus(), MenuStatus.class, null, "菜单状态")
        );
    }

    /** 将应用层批量导入结果转为既有 HTTP 响应格式。 */
    public static ExcelImportResultRes toImportResult(SystemTemplateImportResultDTO result) {
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
                .errors(result.errors() == null ? List.of() : result.errors().stream().map(SystemExcelAssembler::issueText).toList())
                .warnings(result.warnings() == null ? List.of() : result.warnings().stream().map(SystemExcelAssembler::issueText).toList())
                .build();
    }

    private static String issueText(SystemTemplateIssueDTO issue) {
        if (issue == null) {
            return "导入提示";
        }
        String location = issue.row() > 0 ? issue.sheet() + "第 " + issue.row() + " 行" : issue.sheet();
        return location + "：" + issue.message();
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

    private static List<String> splitPaths(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("[;；\\n]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private static String join(List<?> values, String delimiter) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().map(String::valueOf).collect(Collectors.joining(delimiter));
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
