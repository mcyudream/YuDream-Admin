package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;
import java.util.Objects;

/**
 * 快照中一个插件的目录条目。latestDescriptorJson 是 STATIC_INDEX 源同步时最新稳定版本的
 * descriptor 原文（解析仍收敛在 PluginStoreGateway）；LOCAL/V2_API 源改用 structuredVersions。
 */
public record PluginStoreCatalogEntry(String code, String indexUrl, String latestDescriptorJson,
                                      List<PluginStoreCatalogVersion> versions,
                                      List<PluginStoreStructuredVersion> structuredVersions) {

    public PluginStoreCatalogEntry(String code, String indexUrl, String latestDescriptorJson,
                                   List<PluginStoreCatalogVersion> versions) {
        this(code, indexUrl, latestDescriptorJson, versions, null);
    }

    public PluginStoreCatalogEntry {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(indexUrl, "indexUrl");
        versions = versions == null ? List.of() : List.copyOf(versions);
        structuredVersions = structuredVersions == null ? List.of() : List.copyOf(structuredVersions);
    }
}
