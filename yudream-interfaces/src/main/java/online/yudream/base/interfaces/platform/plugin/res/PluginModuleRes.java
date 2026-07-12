package online.yudream.base.interfaces.platform.plugin.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginModuleRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String name;
    private String version;
    private String description;
    private String mainClass;
    private String jarPath;
    private PluginStatus status;
    private String errorMessage;
    private LocalDateTime loadedAt;
    private LocalDateTime enabledAt;

    @Builder.Default
    private List<String> dependencies = new ArrayList<>();

    @Builder.Default
    private List<String> softDependencies = new ArrayList<>();

    private boolean loaded;
    private boolean enabled;
}
