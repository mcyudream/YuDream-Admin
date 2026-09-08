package online.yudream.base.domain.platform.plugin.valobj;

/**
 * 插件全局挂件运行时下发信息：宿主控制台布局常驻挂载的插件远程组件。
 */
public record PluginGlobalWidgetInfo(
        String pluginCode,
        String code,
        String component,
        String permission,
        int sort
) {
}
