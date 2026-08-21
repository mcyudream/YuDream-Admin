package online.yudream.base.domain.platform.plugin.event;

import online.yudream.base.domain.platform.plugin.enumerate.PluginLifecycleAction;
import online.yudream.base.domain.platform.plugin.valobj.PluginRuntimeAssetsDiff;

import java.time.Instant;

/**
 * 插件生命周期与开发模式动作事件，由基础设施层经 Spring ApplicationEventPublisher 发布，
 * 开发者工具 SSE 桥订阅后推送到调试抽屉。
 * assetsDiff 仅开发模式 RELOAD 成功时携带（重载前后运行时资产快照差异），其余动作为 null。
 */
public record PluginLifecycleEvent(
        String pluginCode,
        PluginLifecycleAction action,
        boolean success,
        String version,
        Long durationMs,
        String errorMessage,
        Instant occurredAt,
        PluginRuntimeAssetsDiff assetsDiff
) {
    public PluginLifecycleEvent(String pluginCode, PluginLifecycleAction action, boolean success, String version,
                                Long durationMs, String errorMessage, Instant occurredAt) {
        this(pluginCode, action, success, version, durationMs, errorMessage, occurredAt, null);
    }

    public static PluginLifecycleEvent succeeded(String pluginCode, PluginLifecycleAction action, String version, Long durationMs) {
        return new PluginLifecycleEvent(pluginCode, action, true, version, durationMs, null, Instant.now());
    }

    public static PluginLifecycleEvent succeeded(String pluginCode, PluginLifecycleAction action, String version,
                                                 Long durationMs, PluginRuntimeAssetsDiff assetsDiff) {
        return new PluginLifecycleEvent(pluginCode, action, true, version, durationMs, null, Instant.now(), assetsDiff);
    }

    public static PluginLifecycleEvent failed(String pluginCode, PluginLifecycleAction action, String version, Long durationMs, String errorMessage) {
        return new PluginLifecycleEvent(pluginCode, action, false, version, durationMs, errorMessage, Instant.now());
    }
}
