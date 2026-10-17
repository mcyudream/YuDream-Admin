package online.yudream.base.application.system.monitor.query;

import lombok.Data;
import online.yudream.base.application.common.search.Searchable;

import java.io.Serial;
import java.io.Serializable;

/**
 * 在线会话列表查询。
 * <p>
 * 会话由 Sa-Token 会话缓存过滤后一次性返回（列表接口不分页），沿用既有 limit 上限语义；
 * 前端表格页按 @Searchable 注解生成筛选表单。
 */
@Data
public class OnlineUserQuery implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Searchable(
        label = "关键词",
        placeholder = "用户名 / 昵称 / 邮箱 / 会话标识",
        showWhenFolded = true,
        order = 1,
        width = "240px"
    )
    private String keyword;

    /** 单次返回上限；沿用接口既有默认值（网关内按 200 条封顶）。 */
    private int limit = 100;
}
