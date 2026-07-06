package online.yudream.base.plugin.skin.application.dto;

import online.yudream.base.plugin.skin.domain.valobj.SkinSiteSettings;

public record YuDreamSkinSummaryDTO(
        long users,
        long players,
        long textures,
        long closetItems,
        long options,
        SkinSiteSettings settings
) {
}
