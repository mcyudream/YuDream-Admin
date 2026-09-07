package online.yudream.base.domain.platform.plugin.event;

import online.yudream.base.domain.platform.plugin.enumerate.PluginDevReloadTrigger;

import java.time.Instant;

/**
 * 开发模式请求重载：监听器发现产物变化，或面板登记后需要切到源码目录加载。
 * 由应用层消费并执行完整重载管线。
 */
public record PluginDevReloadRequested(
        String pluginCode,
        Instant occurredAt,
        PluginDevReloadTrigger trigger
) {
    public PluginDevReloadRequested {
        trigger = trigger == null ? PluginDevReloadTrigger.WATCHER : trigger;
    }

    /** 监听器热重载（源码/产物变化） */
    public static PluginDevReloadRequested of(String pluginCode) {
        return new PluginDevReloadRequested(pluginCode, Instant.now(), PluginDevReloadTrigger.WATCHER);
    }

    /** 面板登记后切换到源码目录加载 */
    public static PluginDevReloadRequested ofRegister(String pluginCode) {
        return new PluginDevReloadRequested(pluginCode, Instant.now(), PluginDevReloadTrigger.REGISTER);
    }
}
