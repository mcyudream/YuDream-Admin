package online.yudream.base.domain.platform.plugin.valobj;

import online.yudream.base.domain.common.exception.BizException;

/** 多个插件共用的最大业务类别，不属于任意单个插件。 */
public record PluginMenuCategory(String code, String name, String icon, Integer sort) {

    public PluginMenuCategory {
        code = code == null ? "" : code.trim();
        if (!code.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new BizException("插件菜单分类编码必须为小写字母开头的字母、数字或连字符，长度不超过 64");
        }
        name = name == null || name.isBlank() ? code : name.trim();
        icon = icon == null || icon.isBlank() ? "morph:layout-grid" : icon.trim();
        sort = sort == null ? 0 : sort;
        if (name.length() > 64 || icon.length() > 200) {
            throw new BizException("插件菜单分类名称或图标过长");
        }
    }

    public static PluginMenuCategory named(String code) {
        String normalized = code == null || code.isBlank() ? "other" : code.trim();
        return switch (normalized) {
            case "mc" -> new PluginMenuCategory("mc", "MC", "morph:gamepad-2", 40);
            case "todo" -> new PluginMenuCategory("todo", "待办", "morph:list-todo", 30);
            case "social" -> new PluginMenuCategory("social", "社交", "morph:messages-square", 20);
            case "other" -> new PluginMenuCategory("other", "其他", "morph:layout-grid", 10);
            default -> new PluginMenuCategory(normalized, normalized, null, 0);
        };
    }

    public String menuCode() {
        return "plugin-category:" + code;
    }
}
