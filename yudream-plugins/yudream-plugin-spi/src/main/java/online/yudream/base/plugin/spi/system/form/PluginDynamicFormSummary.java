package online.yudream.base.plugin.spi.system.form;

/**
 * 平台动态表单摘要。
 *
 * @param id          表单 ID（雪花 ID；进入 JSON / URL / 表单时一律序列化为 string）
 * @param code        表单编码，业务唯一
 * @param name        表单名称
 * @param description 表单描述
 * @param status      表单状态（DRAFT / PUBLISHED / DISABLED）
 * @param publishedAt 首次发布时间，epoch 毫秒；未发布过为 0
 */
public record PluginDynamicFormSummary(
        Long id,
        String code,
        String name,
        String description,
        String status,
        long publishedAt
) {
}
