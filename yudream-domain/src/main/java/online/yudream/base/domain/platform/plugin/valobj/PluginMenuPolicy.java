package online.yudream.base.domain.platform.plugin.valobj;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.enumerate.PluginMenuMode;

/** 宿主从 plugin.yml 读取的导航策略，未声明时保留旧菜单结构。 */
public record PluginMenuPolicy(PluginMenuMode mode, PluginMenuCategory category) {

    public PluginMenuPolicy {
        mode = mode == null ? PluginMenuMode.CATEGORY : mode;
        if (mode == PluginMenuMode.CATEGORY && category == null) {
            throw new BizException("插件菜单分类模式必须声明语义分类");
        }
    }

    public static PluginMenuPolicy legacy() {
        return new PluginMenuPolicy(PluginMenuMode.LEGACY, null);
    }

    public boolean categorized() {
        return mode == PluginMenuMode.CATEGORY;
    }
}
