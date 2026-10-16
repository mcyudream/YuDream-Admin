/**
 * 飞书云文档能力端口（SPI 2.37.0 起）。
 *
 * <p>面向插件提供飞书 Drive 云空间、Wiki 知识空间与空间节点列表、docx 文档与文档块的读取，以及块级编辑
 * 原语（追加/删除子块、更新文本 elements）；块与元素以飞书原始 JSON 字符串保真读写。
 * 额度按当前登录用户分钟/日计量，只在宿主「飞书集成」页展示与配置，不经 SPI 下发；
 * 站点目录文件读写走 {@code system.storage.PluginSiteFileStore}（SPI 代理），两者互不混淆。</p>
 */
package online.yudream.base.plugin.spi.system.feishu;
