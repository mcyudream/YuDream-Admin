package online.yudream.base.application.platform.theme.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 主题中心主题卡：内置默认主题或插件主题的可切换视图。
 * 已启用主题带有完整声明信息；已安装未启用的主题插件只持有启用时持久化的基本信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeCenterThemeCardDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主题归属插件编码；null 表示宿主内置默认主题。 */
    private String pluginCode;
    private String code;
    private String name;
    private String description;
    /** 预览图资产相对路径，仅已启用主题可用。 */
    private String preview;
    private String assetRevision;
    /** 是否自带首页方案。 */
    private Boolean hasHomePreset;
    /** 是否声明主题配置 schema（有则主题中心提供配置大页面）。 */
    private Boolean hasConfigSchema;
    /** 是否声明首页接管组件（有则公开站首页由插件 Vue 页面承载，不走 CMS 首页模板）。 */
    private Boolean hasHomeComponent;
    /** 是否声明站点 chrome 接管组件（有则公开站页头/页脚由插件 Vue 页面承载）。 */
    private Boolean hasChromeComponent;
    /** 是否自带页面集（随主题启用导入、停用下线）。 */
    private Boolean hasPageSet;
    /** 是否为当前公开站激活主题。 */
    private Boolean active;
    /** 插件是否已启用（内置主题恒为 true）。 */
    private Boolean enabled;
}
