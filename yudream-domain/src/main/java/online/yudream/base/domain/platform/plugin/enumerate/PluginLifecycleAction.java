package online.yudream.base.domain.platform.plugin.enumerate;

/**
 * 插件生命周期/开发模式动作，用于插件生命周期事件与开发者工具事件流。
 */
public enum PluginLifecycleAction {
    LOAD,
    ENABLE,
    DISABLE,
    UNLOAD,
    RELOAD,
    FRONTEND_RELOAD,
    COMPILE
}
