/**
 * Plugin scoped storage ports exposed by the host framework.
 *
 * <p>{@link online.yudream.base.plugin.spi.system.storage.PluginDocumentStore} 与
 * {@link online.yudream.base.plugin.spi.system.storage.PluginFileStore} 是插件私有数据/对象存储；
 * {@link online.yudream.base.plugin.spi.system.storage.PluginSiteFileStore}（SPI 2.37.0 起）是
 * 站点目录 {@code sites/{siteCode}/plugins/{pluginCode}/} 的 SPI 代理，与飞书云文档只读端口
 * （{@code system.feishu}）互不相干。</p>
 */
package online.yudream.base.plugin.spi.system.storage;
