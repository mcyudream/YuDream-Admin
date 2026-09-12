package online.yudream.base.domain.platform.plugin.port;

import online.yudream.base.domain.platform.plugin.valobj.PluginStoreCatalogEntry;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDescriptor;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreSourceRef;

import java.nio.file.Path;
import java.util.List;

public interface PluginStoreGateway {

    /**
     * 拉取整份目录（按 source.type 分发协议）。每个插件返回其在源内的 index 地址、全部版本的
     * descriptor 绝对地址与最新版 descriptor 原文（STATIC_INDEX），或结构化版本清单（V2_API）。
     * 单个坏插件跳过，根级错误抛 BizException。
     */
    List<PluginStoreCatalogEntry> fetchCatalog(PluginStoreSourceRef source);

    /** 解析快照中保存的 descriptor 原文（仅 STATIC_INDEX）；相对引用按 indexUrl 重新解析，同源同路径校验照常执行。 */
    PluginStorePluginDescriptor parseDescriptor(PluginStoreSourceRef source, String indexUrl, String descriptorJson);

    /** 从源拉取并解析指定版本的 descriptor（仅 STATIC_INDEX）；descriptorUrl/indexUrl 必须属于该源。 */
    PluginStorePluginDescriptor fetchDescriptor(PluginStoreSourceRef source, String indexUrl, String descriptorUrl);

    /** 按 descriptor.jar() 的地址下载 JAR 并校验 SHA-256；LOCAL 源由应用层文件拷贝，不经此方法。 */
    void downloadJar(PluginStoreSourceRef source, PluginStorePluginDescriptor descriptor, Path target);
}
