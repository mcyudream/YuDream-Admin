package online.yudream.base.application.system.user.cmd;

import lombok.Data;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;

import java.io.Serial;
import java.io.Serializable;

/**
 * 模板导入的部门 upsert 指令。
 * <p>
 * 由模板导入服务（如 SystemTemplateAppService）在解析/校验后构造：
 * - 匹配（来源 ID 优先、来源路径逐段解析兜底）与创建/更新分支由调用方完成，
 *   本指令只承载白名单字段的落地值，不参与匹配；
 * - {@code parentId} 必须是调用方已解析的本地父部门 ID（可空表示顶层），禁止传入模板来源 ID；
 *   同批导入的上级部门需由调用方先行创建并回填其本地 ID；
 * - {@code leaderId} 必须是调用方已解析的本地用户 ID；
 * - {@code sourceId} 为模板来源环境部门 ID（已解析为 Long，可空），仅用于审计与日志，
 *   不参与匹配，也禁止写入目标实体（新建部门一律由服务端生成 ID）。
 * <p>
 * 更新语义（{@code applyTemplateUpdate}）：调用方按模板包路径创建本命令时，
 * {@code templateSnapshot=true} 表示白名单字段（包括空值）应完整镜像；
 * 普通局部导入仍可保持 false，以避免缺省误清空既有配置。父部门与主管支持显式指定
 * （{@code parentSpecified}/{@code leaderSpecified} 置 true 后按对应 ID 落库，null 即显式摘除到顶层/清空主管）；
 * 部门名称可变，但新建/更新均沿用「同父同级同名唯一」规则。
 */
@Data
public class TemplateDeptUpsertCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板来源环境部门 ID（可空）；仅用于审计与日志，不参与匹配，禁止写入目标实体。 */
    private Long sourceId;

    /** 模板包完整快照：true 时白名单可空字段按导入值镜像，false 时空值保持现状。 */
    private boolean templateSnapshot;

    /** 部门名称；新建与更新均必填（更新时缺省视为数据错误，避免误清空）。 */
    private String name;

    /** 描述；更新为 null 时保持不变。 */
    private String description;

    /** 已解析的本地主管用户 ID；更新时结合 leaderSpecified 解释（见下）。 */
    private Long leaderId;

    /** 联系电话；更新为 null 或空白时保持不变。 */
    private String phone;

    /** 更新时是否显式指定父部门；true 表示按 parentId 落库（null 即显式摘除到顶层），false 表示未提供、保持现状。新建忽略本字段（parentId 可空即顶层）。 */
    private boolean parentSpecified;

    /** 已解析的本地父部门 ID（可空表示顶层）；更新时结合 parentSpecified 解释。 */
    private Long parentId;

    /** 更新时是否显式指定主管；true 表示按 leaderId 落库（null 即显式清空主管），false 表示未提供、保持现状。新建忽略本字段。 */
    private boolean leaderSpecified;

    /** 排序值；更新为 null 时保持不变。 */
    private Integer sortOrder;

    /** 主色；更新为 null 时保持不变。 */
    private String color;

    /** 新建/更新时的部门状态；null 表示新建默认 ACTIVE、更新保持不变。 */
    private DeptStatus status;
}
