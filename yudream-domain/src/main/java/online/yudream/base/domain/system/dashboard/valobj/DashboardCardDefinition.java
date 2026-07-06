package online.yudream.base.domain.system.dashboard.valobj;

import online.yudream.base.domain.common.exception.BizException;

public record DashboardCardDefinition(
        String code,
        String title,
        String description,
        String icon,
        String category,
        String source,
        String pluginCode,
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
    public DashboardCardDefinition {
        if (isBlank(code)) {
            throw new BizException("卡片编码不能为空");
        }
        if (isBlank(title)) {
            throw new BizException("卡片标题不能为空");
        }
        code = code.trim();
        title = title.trim();
        description = text(description);
        icon = !isBlank(icon) ? icon.trim() : "i-ri:layout-grid-line";
        category = !isBlank(category) ? category.trim() : "系统";
        source = !isBlank(source) ? source.trim() : "SYSTEM";
        pluginCode = text(pluginCode);
        permission = text(permission);
        component = !isBlank(component) ? component.trim() : "ACTION_CARD";
        actionPath = text(actionPath);
        dragPayloadTemplate = text(dragPayloadTemplate);
        tone = !isBlank(tone) ? tone.trim() : "blue";
        defaultW = Math.max(defaultW, 1);
        defaultH = Math.max(defaultH, 1);
        minW = Math.max(minW, 1);
        minH = Math.max(minH, 1);
    }

    private static String text(String value) {
        return !isBlank(value) ? value.trim() : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
