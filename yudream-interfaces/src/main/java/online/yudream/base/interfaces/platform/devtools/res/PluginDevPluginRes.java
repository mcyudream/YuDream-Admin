package online.yudream.base.interfaces.platform.devtools.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevProjectInfo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 开发者工具插件清单项响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginDevPluginRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private String name;
    private String version;
    private String description;
    private PluginStatus status;
    private boolean loaded;
    private boolean enabled;
    private boolean devMode;
    private PluginDevProjectInfo devProject;
    /** 硬依赖插件编码（plugin.yml depend） */
    private List<String> dependencies;
    /** 软依赖插件编码（plugin.yml softdepend） */
    private List<String> softDependencies;
}
