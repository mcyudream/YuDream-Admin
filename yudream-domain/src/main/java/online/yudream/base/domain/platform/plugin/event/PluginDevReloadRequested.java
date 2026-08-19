package online.yudream.base.domain.platform.plugin.event;

import java.time.Instant;

/**
 * 开发模式监听器检测到插件产物变化后请求重载，由应用层消费并执行完整重载管线。
 */
public record PluginDevReloadRequested(
        String pluginCode,
        Instant occurredAt
) {
    public static PluginDevReloadRequested of(String pluginCode) {
        return new PluginDevReloadRequested(pluginCode, Instant.now());
    }
}
