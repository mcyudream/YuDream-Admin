package online.yudream.base.plugin.spi.theme;

/**
 * 主题块调用上下文：宿主解析公开站模板中的 {@code blocks.{code}} 引用时传入。
 *
 * @param themeCode 当前激活的 SITE 主题 code（内置主题为 {@code "default"}）
 * @param limit     模板声明的数据条数上限（{@code data-yb-limit}），无声明时为宿主默认值
 */
public record PluginThemeBlockContext(String themeCode, int limit) {
}
