package online.yudream.base.domain.platform.plugin.valobj;

import online.yudream.base.domain.common.exception.BizException;

import java.util.LinkedHashMap;
import java.util.Map;

/** 多个插件共用的最大业务类别，不属于任意单个插件。 */
public record PluginMenuCategory(String code, String name, String icon, Integer sort) {

    /** 一级菜单名称上限：图标栏标签区只容纳 5 个汉字。 */
    public static final int MAX_NAME_LENGTH = 5;

    /** 宿主内置语义分类：插件在 plugin.yml 以 code 声明，不提供「其他」兜底。 */
    private static final Map<String, PluginMenuCategory> BUILT_IN = builtIn();

    public PluginMenuCategory {
        code = code == null ? "" : code.trim();
        if (!code.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new BizException("插件菜单分类编码必须为小写字母开头的字母、数字或连字符，长度不超过 64");
        }
        name = name == null || name.isBlank() ? code : name.trim();
        icon = icon == null || icon.isBlank() ? "morph:layout-grid" : icon.trim();
        sort = sort == null ? 0 : sort;
        if (displayLength(name) > MAX_NAME_LENGTH) {
            throw new BizException("插件菜单分类名称最多 " + MAX_NAME_LENGTH + " 个字：" + name);
        }
        if (icon.length() > 200) {
            throw new BizException("插件菜单分类图标过长");
        }
    }

    public static PluginMenuCategory named(String code) {
        String normalized = code == null || code.isBlank() ? "" : code.trim();
        PluginMenuCategory builtIn = BUILT_IN.get(normalized);
        if (builtIn != null) {
            return builtIn;
        }
        return new PluginMenuCategory(normalized, normalized, null, 0);
    }

    /** 内置语义分类；自定义编码返回 {@code null}，由调用方补名称、图标与排序。 */
    public static PluginMenuCategory builtIn(String code) {
        String normalized = code == null ? "" : code.trim();
        return BUILT_IN.get(normalized);
    }

    public String menuCode() {
        return "plugin-category:" + code;
    }

    /** 一级菜单名称按 Unicode 码点计数。 */
    public static int displayLength(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    private static Map<String, PluginMenuCategory> builtIn() {
        // 运行导航按 sort 降序排列：数值越大越靠前，宿主模块（1~2）固定在最末。
        Map<String, PluginMenuCategory> categories = new LinkedHashMap<>();
        categories.put("appearance", new PluginMenuCategory("appearance", "外观", "morph:palette", 110));
        categories.put("mc", new PluginMenuCategory("mc", "MC", "morph:gamepad-2", 100));
        categories.put("social", new PluginMenuCategory("social", "社交", "morph:messages-square", 90));
        categories.put("content", new PluginMenuCategory("content", "内容", "morph:newspaper", 80));
        categories.put("campus", new PluginMenuCategory("campus", "校园", "morph:graduation-cap", 70));
        categories.put("client", new PluginMenuCategory("client", "客户端", "morph:monitor-down", 60));
        categories.put("trade", new PluginMenuCategory("trade", "交易", "morph:coins", 50));
        categories.put("play", new PluginMenuCategory("play", "娱乐", "morph:party-popper", 40));
        categories.put("todo", new PluginMenuCategory("todo", "待办", "morph:list-todo", 30));
        categories.put("tools", new PluginMenuCategory("tools", "工具", "morph:wrench", 20));
        return Map.copyOf(categories);
    }
}
