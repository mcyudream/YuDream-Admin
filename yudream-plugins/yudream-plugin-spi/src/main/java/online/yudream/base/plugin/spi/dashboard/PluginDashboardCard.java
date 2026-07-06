package online.yudream.base.plugin.spi.dashboard;

public record PluginDashboardCard(
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
