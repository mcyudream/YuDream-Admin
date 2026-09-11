package online.yudream.base.domain.platform.plugin.valobj;

public class PluginStorePluginVersion {

    private final String releaseVersion;
    private final PluginStorePluginDescriptor descriptor;
    private final String sourceCode;
    private final String sourceName;

    public PluginStorePluginVersion(String releaseVersion, PluginStorePluginDescriptor descriptor) {
        this(releaseVersion, descriptor, null, null);
    }

    public PluginStorePluginVersion(String releaseVersion, PluginStorePluginDescriptor descriptor,
                                    String sourceCode, String sourceName) {
        this.releaseVersion = releaseVersion;
        this.descriptor = descriptor;
        this.sourceCode = sourceCode;
        this.sourceName = sourceName;
    }

    public String releaseVersion() {
        return releaseVersion;
    }

    public PluginStorePluginDescriptor descriptor() {
        return descriptor;
    }

    public String sourceCode() {
        return sourceCode;
    }

    public String sourceName() {
        return sourceName;
    }
}
