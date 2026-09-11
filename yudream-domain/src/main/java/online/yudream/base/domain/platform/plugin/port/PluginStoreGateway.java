package online.yudream.base.domain.platform.plugin.port;

import online.yudream.base.domain.platform.plugin.valobj.PluginStoreCatalogEntry;
import online.yudream.base.domain.platform.plugin.valobj.PluginStorePluginDescriptor;
import online.yudream.base.domain.platform.plugin.valobj.PluginStoreSourceRef;

import java.nio.file.Path;
import java.util.List;

public interface PluginStoreGateway {

    /** 配置文件直连的内置源：能力未启用时市场回落到该单源。 */
    PluginStoreSourceRef configuredSourceRef();

    /**
     * 拉取整份目录。每个插件返回其在源内的 index 地址、全部版本的 descriptor 绝对地址，
     * 以及最新版本的 descriptor 原文（未解析，后续按 indexUrl 重新解析）。单个坏插件跳过，
     * 根索引/模式错误抛 BizException。
     */
    List<PluginStoreCatalogEntry> fetchCatalog(PluginStoreSourceRef source);

    /** 解析快照中保存的 descriptor 原文；相对引用按 indexUrl 重新解析，同源同路径校验照常执行。 */
    PluginStorePluginDescriptor parseDescriptor(PluginStoreSourceRef source, String indexUrl, String descriptorJson);

    /** 从源拉取并解析指定版本的 descriptor；descriptorUrl/indexUrl 必须属于该源。 */
    PluginStorePluginDescriptor fetchDescriptor(PluginStoreSourceRef source, String indexUrl, String descriptorUrl);

    void downloadJar(PluginStoreSourceRef source, PluginStorePluginDescriptor descriptor, Path target);
}
