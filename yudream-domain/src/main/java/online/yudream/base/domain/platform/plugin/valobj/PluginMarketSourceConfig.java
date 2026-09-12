package online.yudream.base.domain.platform.plugin.valobj;

import java.util.Map;

/**
 * 插件市场源能力配置键。defaultConfig 只播种空配置行，读取处运行时回落默认值。
 */
public final class PluginMarketSourceConfig {

    /** 发布是否需要审核；未配置或非 false 时默认需要审核。 */
    public static final String REVIEW_REQUIRED = "reviewRequired";

    /** 是否对外提供公开社区与 v2/legacy 只读协议；未配置或非 false 时默认开启。 */
    public static final String PUBLIC_ENABLED = "publicEnabled";

    private PluginMarketSourceConfig() {
    }

    public static boolean reviewRequired(Map<String, String> config) {
        return !"false".equalsIgnoreCase(value(config, REVIEW_REQUIRED));
    }

    public static boolean publicEnabled(Map<String, String> config) {
        return !"false".equalsIgnoreCase(value(config, PUBLIC_ENABLED));
    }

    private static String value(Map<String, String> config, String key) {
        return config == null ? null : config.get(key);
    }
}
