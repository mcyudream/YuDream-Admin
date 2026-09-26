/**
 * 插件 WebSocket 端点契约（SPI 2.33.0 起）。
 *
 * <p>插件经 {@code PluginContext.registerWebSocketHandler(path, permission, handler)} 注册
 * WebSocket 处理器；宿主负责挂载 {@code /api/platform/plugin-ws/{pluginCode}/**}、握手鉴权
 * （Authorization 头、单次票据或匿名）、同源校验、发送缓冲限制，以及插件
 * disable/unload/reload 时强制关闭该插件的全部会话并移除注册。
 */
package online.yudream.base.plugin.spi.ws;
