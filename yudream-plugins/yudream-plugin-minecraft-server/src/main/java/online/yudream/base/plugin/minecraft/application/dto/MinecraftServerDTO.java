package online.yudream.base.plugin.minecraft.application.dto;

import java.util.List;

public record MinecraftServerDTO(
        String id,
        String name,
        String descriptionMarkdown,
        boolean enabled,
        int sort,
        List<EndpointDTO> endpoints,
        List<SeasonDTO> seasons,
        SeasonDTO currentSeason,
        MinecraftServerStatusDTO status,
        long createdAt,
        long updatedAt
) {

    public record EndpointDTO(
            String id,
            String name,
            String host,
            int port,
            String edition,
            boolean primaryLine,
            boolean enabled,
            int sort
    ) {
    }

    public record SeasonDTO(
            String id,
            String name,
            String description,
            Long startedAt,
            Long endedAt,
            boolean current,
            int sort
    ) {
    }
}
