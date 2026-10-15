package online.yudream.base.plugin.spi.guide;

/**
 * 引导步骤：{@code target} 为 {@code [data-tour="..."]} 形式的稳定选择器，
 * 为空表示该步骤居中展示；{@code placement} 取 top/bottom/left/right，默认 bottom。
 */
public record PluginGuideStep(
        String target,
        String title,
        String content,
        String placement
) {
}
