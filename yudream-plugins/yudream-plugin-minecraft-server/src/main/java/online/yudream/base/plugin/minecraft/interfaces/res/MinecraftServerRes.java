package online.yudream.base.plugin.minecraft.interfaces.res;

import java.util.List;

public record MinecraftServerRes(
        String id,
        String name,
        String descriptionMarkdown,
        boolean enabled,
        int sort,
        List<EndpointRes> endpoints,
        List<SeasonRes> seasons,
        SeasonRes currentSeason,
        MinecraftServerStatusRes status,
        long createdAt,
        long updatedAt
) {

    public record EndpointRes(
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

    public record SeasonRes(
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
