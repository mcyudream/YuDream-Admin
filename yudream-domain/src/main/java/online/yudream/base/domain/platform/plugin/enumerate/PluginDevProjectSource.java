package online.yudream.base.domain.platform.plugin.enumerate;

/**
 * 开发模式插件项目的登记来源：配置文件静态登记，或由开发者面板写入本地清单文件。
 */
public enum PluginDevProjectSource {
    /** 来自 yml 配置 yudream.platform.plugin.dev-mode.projects，面板只读 */
    CONFIG,
    /** 来自开发者面板维护的本地清单文件（默认 plugins/dev-projects.json），面板可增删 */
    FILE
}
