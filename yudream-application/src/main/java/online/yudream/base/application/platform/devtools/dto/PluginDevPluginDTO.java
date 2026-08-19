package online.yudream.base.application.platform.devtools.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;
import online.yudream.base.domain.platform.plugin.valobj.PluginDevProjectInfo;

/**
 * 开发者工具视角的插件清单项：在管理列表基础上叠加开发模式标记与项目配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginDevPluginDTO {

    private String code;
    private String name;
    private String version;
    private String description;
    private PluginStatus status;
    private boolean loaded;
    private boolean enabled;
    private boolean devMode;
    /** 开发模式项目配置，仅 devMode=true 时有值 */
    private PluginDevProjectInfo devProject;
}
