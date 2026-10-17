package online.yudream.base.application.system.monitor.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.application.common.search.Searchable;

/**
 * 在线会话分页查询。
 * <p>
 * 会话由 Sa-Token 令牌存储枚举，总量受令牌检索上限约束，因此服务端在枚举结果上按
 * 「最近活跃优先」排序后切片；前端表格页按 @Searchable 注解生成筛选表单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OnlineUserQuery extends PageQuery {

    @Searchable(
        label = "关键词",
        placeholder = "用户名 / 昵称 / 邮箱 / 会话标识",
        showWhenFolded = true,
        order = 1,
        width = "240px"
    )
    private String keyword;
}
