package online.yudream.base.interfaces.platform.theme.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ThemeCenterThemeRes {

    /** 主题归属插件编码；null 表示宿主内置默认主题。 */
    private String pluginCode;
    private String code;
    private String name;
    private String description;
    private String preview;
    private String assetRevision;
    private Boolean hasHomePreset;
    private Boolean hasPageSet;
    private Boolean active;
    private Boolean enabled;
}
