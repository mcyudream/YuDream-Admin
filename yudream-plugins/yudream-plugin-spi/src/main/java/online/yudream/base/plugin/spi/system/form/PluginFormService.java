package online.yudream.base.plugin.spi.system.form;

import java.util.List;
import java.util.Optional;

/**
 * 动态表单能力端口：插件读取平台表单与提交记录。
 *
 * 宿主能力 code 为 {@code form}，可在能力管理中被关闭；除 {@link #enabled()} 外，
 * 宿主实现在能力未启用时抛 {@link IllegalArgumentException}。插件应先判断再调用并显式降级。
 * 全部方法提供 default 实现以保证契约可编译演进，真实行为以宿主实现为准。
 */
public interface PluginFormService {

    /**
     * 表单能力（{@code form}）是否已启用。
     */
    default boolean enabled() {
        return false;
    }

    /**
     * 分页搜索已发布（PUBLISHED）的表单摘要，供插件构建 options/selector。
     * page 从 1 开始；size 由宿主钳制到 [1, 200]，非正数按 20 处理。
     */
    default List<PluginDynamicFormSummary> publishedForms(String keyword, int page, int size) {
        return List.of();
    }

    /**
     * 按表单 code 查询表单摘要（不限状态）；空白 code 或不存在返回 empty。
     */
    default Optional<PluginDynamicFormSummary> formByCode(String code) {
        return Optional.empty();
    }

    /**
     * 判断指定用户是否在时间窗口内提交过指定表单。
     * fromEpochMillis / toEpochMillis ≤ 0 表示该端不限制；formCode 空白或 submitterId 为 null 返回 false。
     */
    default boolean submittedBy(String formCode, Long submitterId, long fromEpochMillis, long toEpochMillis) {
        return false;
    }
}
