package online.yudream.base.application.platform.plugin.dto;

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
public class PluginStorePluginDescriptorDTO implements Serializable {

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
    private PluginStorePluginPublisherDTO publisher;
    private PluginStorePluginSourceDTO source;
    private String license;
    private String releaseNotes;
    private PluginStorePluginCompatibilityDTO compatibility;
    private List<PluginStorePluginDependencyDTO> dependencies;
    private PluginStorePluginJarDTO jar;
}
