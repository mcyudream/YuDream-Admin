package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;
import java.util.Objects;

/**
 * 快照中一个插件的目录条目。latestDescriptorJson 是同步时最新稳定版本的 descriptor 原文，
 * 解析工作仍收敛在 PluginStoreGateway（按 indexUrl 重新解析相对引用），快照本身不持有解析结果。
 */
public record PluginStoreCatalogEntry(String code, String indexUrl, String latestDescriptorJson,
                                      List<PluginStoreCatalogVersion> versions) {

    public PluginStoreCatalogEntry {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(indexUrl, "indexUrl");
        Objects.requireNonNull(latestDescriptorJson, "latestDescriptorJson");
        versions = versions == null ? List.of() : List.copyOf(versions);
    }
}
