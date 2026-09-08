package online.yudream.base.plugin.spi.widget;

/**
 * 插件全局挂件注册项：宿主控制台布局常驻挂载的远程组件声明。
 */
public record PluginGlobalWidget(
        String code,
        String component,
        String permission,
        int sort
) {
}
