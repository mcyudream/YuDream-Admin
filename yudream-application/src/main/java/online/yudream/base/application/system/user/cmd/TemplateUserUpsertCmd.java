package online.yudream.base.application.system.user.cmd;

import lombok.Data;
import online.yudream.base.domain.system.user.enumerate.UserStatus;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 模板导入的用户 upsert 指令。
 * <p>
 * 由模板导入服务（如 SystemTemplateAppService）在解析/校验后构造：
 * - {@code sourceId} 为上一轮导入返回的用户 ID（数字字符串）时按此 ID 优先定位目标账号
 *   （username 必须一致，否则视为失效映射退回 username 解析）；其它取值仅用于审计与日志；
 * - {@code username} 是身份键（sourceId 未命中时的解析口径）：已存在【已验证】用户即更新，
 *   不存在即新建；
 * - 已存在用户保持 username/password 不变（本指令不携带密码字段）；
 * - 新建用户由服务端生成占位密码（不导出、不返回），并通过初始密码设置邮件完成首次登录；
 * - {@code roleIds}/{@code deptIds} 为模板服务已解析的目标角色/部门；
 * - {@code sourceId} 为模板来源标识（模板行/文件等），仅用于审计与日志，不参与匹配。
 */
@Data
public class TemplateUserUpsertCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模板来源标识。为上一轮导入返回的用户 ID（数字字符串）时按 ID 优先定位目标账号，
     * username 必须与模板行一致，否则按 username 解析兜底；username 解析无业务键目标
     * 时安全失败（不得新建）；其它取值不参与匹配，仅审计。
     */
    private String sourceId;

    /** 模板包完整快照：true 时空部门/角色集合也按导入值覆盖；false 时空集合保持现状。 */
    private boolean templateSnapshot;

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private String qq;

    /** 新建/更新时的邮箱验证状态；null 表示新建默认 false、更新不改。 */
    private Boolean emailVerified;

    /** 新建/更新时的用户状态；null 表示新建默认 ACTIVE、更新不改。 */
    private UserStatus status;

    /** 已解析的目标部门（可空）；null 或空表示更新时保持不变。 */
    private List<Long> deptIds = new ArrayList<>();

    /** 默认部门（可空）；未提供时取 deptIds 首个或系统根部门。 */
    private Long defaultDeptId;

    /** 已解析的目标角色（可空）；null 或空表示更新时保持不变。 */
    private List<Long> roleIds = new ArrayList<>();
}
