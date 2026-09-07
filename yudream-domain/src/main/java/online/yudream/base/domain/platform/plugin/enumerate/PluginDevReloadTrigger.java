package online.yudream.base.domain.platform.plugin.enumerate;

/**
 * 开发模式重载请求来源：源码产物变化由监听器发起，登记/批量登记切到源码加载由面板发起。
 */
public enum PluginDevReloadTrigger {
    /** 监听器发现 classes 变化，始终执行重载 */
    WATCHER,
    /** 面板登记或批量登记后的源码切换；若该插件刚被重载或级联恢复则抑制，避免重复重载 */
    REGISTER
}
