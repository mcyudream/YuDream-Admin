package online.yudream.base.interfaces.platform.theme.assembler;

import online.yudream.base.application.platform.theme.dto.ThemePublicContextDTO;
import online.yudream.base.interfaces.platform.cms.assembler.CmsTemplateContextWebAssembler;
import online.yudream.base.interfaces.platform.theme.res.ThemePublicContextRes;

import java.util.List;

public final class ThemePublicContextWebAssembler {

    private ThemePublicContextWebAssembler() {
    }

    public static ThemePublicContextRes toRes(ThemePublicContextDTO dto) {
        return ThemePublicContextRes.builder()
                .themeCode(dto.getThemeCode())
                .themeConfig(dto.getThemeConfig())
                .blocks(dto.getBlocks())
                .cmsPagesLatest(dto.getCmsPagesLatest() == null ? List.of() : dto.getCmsPagesLatest().stream()
                        .map(CmsTemplateContextWebAssembler::toItemRes)
                        .toList())
                .build();
    }
}
