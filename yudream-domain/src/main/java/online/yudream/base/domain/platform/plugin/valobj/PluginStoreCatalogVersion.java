package online.yudream.base.domain.platform.plugin.valobj;

import java.util.Objects;

/** 快照中某插件的一个版本：releaseVersion + 已按源根解析为绝对地址的 descriptor URL。 */
public record PluginStoreCatalogVersion(String releaseVersion, String descriptorUrl) {

    public PluginStoreCatalogVersion {
        Objects.requireNonNull(releaseVersion, "releaseVersion");
        Objects.requireNonNull(descriptorUrl, "descriptorUrl");
    }
}
