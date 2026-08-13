package online.yudream.base.interfaces.platform.plugin.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginStorePluginDescriptorRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String releaseVersion;
    private String code;
    private String version;
    private String main;
    private String displayName;
    private String description;
    private String icon;
    private List<String> screenshots;
    private PluginStorePluginPublisherRes publisher;
    private PluginStorePluginSourceRes source;
    private String license;
    private String releaseNotes;
    private PluginStorePluginCompatibilityRes compatibility;
    private List<PluginStorePluginDependencyRes> dependencies;
    private PluginStorePluginJarRes jar;
}
