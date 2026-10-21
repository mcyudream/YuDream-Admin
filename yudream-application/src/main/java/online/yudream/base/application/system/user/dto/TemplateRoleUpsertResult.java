package online.yudream.base.application.system.user.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 模板导入角色 upsert 结果。
 */
@Getter
@Builder
public class TemplateRoleUpsertResult {

    /** true=新建角色，false=更新既有角色。 */
    private final boolean created;

    /** 本地角色 ID（新建时为服务端生成，不回传模板来源 ID）。 */
    private final Long roleId;

    private final String code;

    private final String name;
}
