package online.yudream.base.application.system.user.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 模板导入部门 upsert 结果。
 */
@Getter
@Builder
public class TemplateDeptUpsertResult {

    /** true=新建部门，false=更新既有部门。 */
    private final boolean created;

    /** 本地部门 ID（新建时为服务端生成，不回传模板来源 ID）。 */
    private final Long deptId;

    private final String name;

    /** 本地父部门 ID（顶层为 null）。 */
    private final Long parentId;
}
