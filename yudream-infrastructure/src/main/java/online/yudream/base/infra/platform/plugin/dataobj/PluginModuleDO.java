package online.yudream.base.infra.platform.plugin.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformPlugin")
public class PluginModuleDO extends BaseDO {

    @Indexed(unique = true)
    private String code;
    private String name;
    private String pluginVersion;
    private String description;
    private String mainClass;
    private String jarPath;
    private List<String> dependencies;
    private PluginStatus status;
    private String errorMessage;
    private LocalDateTime loadedAt;
    private LocalDateTime enabledAt;
    private Boolean menusInitialized;
}
