package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;

public class PluginStorePluginDescriptor {

    private final String releaseVersion;
    private final String code;
    private final String version;
    private final String main;
    private final String displayName;
    private final String description;
    private final String icon;
    private final List<String> screenshots;
    private final PluginStorePluginPublisher publisher;
    private final PluginStorePluginSource source;
    private final String license;
    private final String releaseNotes;
    private final PluginStorePluginCompatibility compatibility;
    private final List<PluginStorePluginDependency> dependencies;
    private final PluginStorePluginJar jar;

    public PluginStorePluginDescriptor(String releaseVersion, String code, String version, String main,
                                       String displayName, String description, String icon, List<String> screenshots,
                                       PluginStorePluginCompatibility compatibility, List<PluginStorePluginDependency> dependencies,
                                       PluginStorePluginJar jar) {
        this(releaseVersion, code, version, main, displayName, description, icon, screenshots, null, null, null,
                null, compatibility, dependencies, jar);
    }

    public PluginStorePluginDescriptor(String releaseVersion, String code, String version, String main,
                                       String displayName, String description, String icon, List<String> screenshots,
                                       PluginStorePluginPublisher publisher, PluginStorePluginSource source, String license,
                                       String releaseNotes, PluginStorePluginCompatibility compatibility,
                                       List<PluginStorePluginDependency> dependencies, PluginStorePluginJar jar) {
        this.releaseVersion = releaseVersion;
        this.code = code;
        this.version = version;
        this.main = main;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.screenshots = screenshots == null ? List.of() : List.copyOf(screenshots);
        this.publisher = publisher;
        this.source = source;
        this.license = license;
        this.releaseNotes = releaseNotes;
        this.compatibility = compatibility;
        this.dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        this.jar = jar;
    }

    public String releaseVersion() {
        return releaseVersion;
    }

    public String code() {
        return code;
    }

    public String version() {
        return version;
    }

    public String main() {
        return main;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String icon() {
        return icon;
    }

    public List<String> screenshots() {
        return screenshots;
    }

    public PluginStorePluginPublisher publisher() {
        return publisher;
    }

    public PluginStorePluginSource source() {
        return source;
    }

    public String license() {
        return license;
    }

    public String releaseNotes() {
        return releaseNotes;
    }

    public PluginStorePluginCompatibility compatibility() {
        return compatibility;
    }

    public List<PluginStorePluginDependency> dependencies() {
        return dependencies;
    }

    public PluginStorePluginJar jar() {
        return jar;
    }
}
