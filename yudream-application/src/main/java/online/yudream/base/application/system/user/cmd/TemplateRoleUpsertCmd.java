package online.yudream.base.application.system.user.cmd;

import lombok.Data;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 模板导入的角色 upsert 指令。
 * <p>
 * 由模板导入服务（如 SystemTemplateAppService）在解析/校验后构造：
 * - 匹配（来源 ID 优先、角色编码兜底）与创建/更新分支由调用方完成，
 *   本指令只承载白名单字段的落地值，不参与匹配；
 * - {@code deptId} 必须是调用方已解析的本地部门 ID，禁止传入模板来源 ID；
 * - {@code sourceId} 为模板来源环境角色 ID（已解析为 Long，可空），仅用于审计与日志，
 *   不参与匹配，也禁止写入目标实体（新建角色一律由服务端生成 ID）。
 * <p>
 * 更新语义（{@code applyTemplateUpdate}）：{@code templateSnapshot=true} 时模板中的
 * 白名单字段按完整快照镜像（空权限代表清空）；普通局部导入保持 false 时，null 字段保持现状。
 */
@Data
public class TemplateRoleUpsertCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板来源环境角色 ID（可空）；仅用于审计与日志，不参与匹配，禁止写入目标实体。 */
    private Long sourceId;

    /** 模板包完整快照：true 时可空白名单字段按导入值镜像，false 时 null 字段保持现状。 */
    private boolean templateSnapshot;

    /** 角色名称；新建与更新均必填（更新时缺省视为数据错误，避免误清空）。 */
    private String name;

    /** 角色编码（业务键）；新建必填；更新时编码不可变，非空且与目标不一致将抛冲突异常。 */
    private String code;

    /** 已解析的本地目标部门 ID；新建必填；更新为 null 时保持不变。 */
    private Long deptId;

    /** 角色权重；更新为 null 时保持不变。 */
    private RoleLevel level;

    /** 图标；更新为 null 时保持不变。 */
    private String icon;

    /** 代表色；更新为 null 时保持不变。 */
    private String color;

    /** 权限码集合；模板包完整快照中的空列表代表清空，局部更新的 null 代表保持不变。 */
    private List<String> permissions;

    /** 新建/更新时的角色状态；null 表示新建默认 ACTIVE、更新保持不变。 */
    private RoleStatus status;
}
