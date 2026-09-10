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
    private Boolean cmsEnabled;
}
