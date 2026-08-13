package online.yudream.base.domain.platform.plugin.valobj;

import java.util.List;

public class PluginStorePluginDetail {

    private final String code;
    private final List<PluginStorePluginVersion> versions;

    public PluginStorePluginDetail(String code, List<PluginStorePluginVersion> versions) {
        this.code = code;
        this.versions = versions;
    }

    public String code() {
        return code;
    }

    public List<PluginStorePluginVersion> versions() {
        return versions;
    }
}
