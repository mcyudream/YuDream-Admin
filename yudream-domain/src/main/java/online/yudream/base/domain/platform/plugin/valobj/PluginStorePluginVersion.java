package online.yudream.base.domain.platform.plugin.valobj;

public class PluginStorePluginVersion {

    private final String releaseVersion;
    private final PluginStorePluginDescriptor descriptor;

    public PluginStorePluginVersion(String releaseVersion, PluginStorePluginDescriptor descriptor) {
        this.releaseVersion = releaseVersion;
        this.descriptor = descriptor;
    }

    public String releaseVersion() {
        return releaseVersion;
    }

    public PluginStorePluginDescriptor descriptor() {
        return descriptor;
    }
}
