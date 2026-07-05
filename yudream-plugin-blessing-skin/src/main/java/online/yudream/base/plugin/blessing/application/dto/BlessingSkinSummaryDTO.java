package online.yudream.base.plugin.blessing.application.dto;

import online.yudream.base.plugin.blessing.domain.valobj.SkinSiteSettings;

public record BlessingSkinSummaryDTO(
        long users,
        long players,
        long textures,
        long closetItems,
        long options,
        SkinSiteSettings settings
) {
}
