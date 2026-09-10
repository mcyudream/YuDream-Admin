package online.yudream.base.interfaces.platform.theme.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.interfaces.platform.cms.res.HomePagePresetRes;

import java.util.List;

@Data
@Builder
public class ThemeCenterOverviewRes {

    private List<ThemeCenterThemeRes> themes;
    private List<HomePagePresetRes> presets;
    /** 可在主题中心编辑内容的主题编码清单。 */
    private List<String> editableThemes;
    private Boolean cmsEnabled;
}
