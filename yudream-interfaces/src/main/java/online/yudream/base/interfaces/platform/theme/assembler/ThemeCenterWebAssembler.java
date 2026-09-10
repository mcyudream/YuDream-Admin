package online.yudream.base.interfaces.platform.theme.assembler;

import online.yudream.base.application.platform.theme.dto.ThemeCenterOverviewDTO;
import online.yudream.base.application.platform.theme.dto.ThemeCenterThemeCardDTO;
import online.yudream.base.interfaces.platform.cms.assembler.CmsWebAssembler;
import online.yudream.base.interfaces.platform.theme.res.ThemeCenterOverviewRes;
import online.yudream.base.interfaces.platform.theme.res.ThemeCenterThemeRes;

import java.util.List;

public class ThemeCenterWebAssembler {

    public static ThemeCenterThemeRes toRes(ThemeCenterThemeCardDTO dto) {
        return ThemeCenterThemeRes.builder()
                .pluginCode(dto.getPluginCode())
                .code(dto.getCode())
                .name(dto.getName())
                .description(dto.getDescription())
                .preview(dto.getPreview())
                .assetRevision(dto.getAssetRevision())
                .hasHomePreset(dto.getHasHomePreset())
                .hasPageSet(dto.getHasPageSet())
                .active(dto.getActive())
                .enabled(dto.getEnabled())
                .build();
    }

    public static ThemeCenterOverviewRes toRes(ThemeCenterOverviewDTO dto) {
        List<ThemeCenterThemeRes> themes = dto.getThemes().stream()
                .map(ThemeCenterWebAssembler::toRes)
                .toList();
        return ThemeCenterOverviewRes.builder()
                .themes(themes)
                .presets(CmsWebAssembler.toPresetResList(dto.getPresets()))
                .editableThemes(dto.getEditableThemes())
                .cmsEnabled(dto.getCmsEnabled())
                .build();
    }
}
