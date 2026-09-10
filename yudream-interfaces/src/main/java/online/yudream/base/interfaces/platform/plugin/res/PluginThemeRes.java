package online.yudream.base.interfaces.platform.plugin.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginThemeRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String pluginCode;
    private String code;
    private String name;
    private String description;
    private Set<String> scopes;
    private List<String> styles;
    private String preview;
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
