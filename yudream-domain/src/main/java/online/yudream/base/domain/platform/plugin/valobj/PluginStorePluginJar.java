package online.yudream.base.domain.platform.plugin.valobj;

public class PluginStorePluginJar {

    private final String mavenCoordinates;
    private final String url;
    private final String sha256;

    public PluginStorePluginJar(String mavenCoordinates, String url, String sha256) {
        this.mavenCoordinates = mavenCoordinates;
        this.url = url;
        this.sha256 = sha256;
    }

    public String mavenCoordinates() {
        return mavenCoordinates;
    }

    public String url() {
        return url;
    }

    public String sha256() {
        return sha256;
    }
}
