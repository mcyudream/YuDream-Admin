package online.yudream.base.domain.platform.plugin.valobj;

import online.yudream.base.domain.platform.plugin.enumerate.PluginMenuMode;

/** 宿主从 plugin.yml 读取的导航策略，未声明时保留旧菜单结构。 */
public record PluginMenuPolicy(PluginMenuMode mode, PluginMenuCategory category) {

    public PluginMenuPolicy {
        mode = mode == null ? PluginMenuMode.CATEGORY : mode;
        category = category == null ? PluginMenuCategory.named("other") : category;
    }

    public static PluginMenuPolicy legacy() {
        return new PluginMenuPolicy(PluginMenuMode.LEGACY, null);
    }

    public boolean categorized() {
        return mode == PluginMenuMode.CATEGORY;
    }
}
