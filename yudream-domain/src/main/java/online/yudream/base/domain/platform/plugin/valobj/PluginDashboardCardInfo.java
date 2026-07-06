package online.yudream.base.domain.platform.plugin.valobj;

public record PluginDashboardCardInfo(
        String pluginCode,
        String code,
        String title,
        String description,
        String icon,
        String category,
        String permission,
        String component,
        String actionPath,
        String dragPayloadTemplate,
        String tone,
        int defaultW,
        int defaultH,
        int minW,
        int minH,
        int sort
) {
}
