package online.yudream.base.domain.platform.plugin.valobj;

/**
 * 插件引导步骤运行时下发信息：target 为 [data-tour="..."] 选择器，空表示居中步骤。
 */
public record PluginGuideStepInfo(
        String target,
        String title,
        String content,
        String placement
) {
}
