package online.yudream.base.application.platform.plugin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * 插件主题信息：已启用插件声明的界面主题。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginThemeDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String pluginCode;
    private String code;
    private String name;
    private String description;
    /** 生效范围：SITE / ADMIN。 */
    private Set<String> scopes;
    /** 主题 CSS 资产相对路径，按声明顺序注入。 */
    private List<String> styles;
    private String preview;
    /** 首页内容定制方案资产相对路径，空表示该主题不自带方案。 */
    private String homePreset;
    /** 主题配置 schema 资产相对路径，空表示该主题不声明配置页。 */
    private String configSchema;
    /** 首页接管组件（远程模块 routes 导出键），空表示首页仍由 CMS 承载。 */
    private String homeComponent;
    /** 站点 chrome 接管组件（远程模块 routes 导出键），空表示仍由宿主 SiteChrome 承载。 */
    private String chromeComponent;
    /** 首页/chrome 组件所在远程模块名。 */
    private String moduleName;
    private String assetRevision;
}
