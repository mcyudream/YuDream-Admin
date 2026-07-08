package online.yudream.base.plugin.minecraft.application.cmd;

import java.util.List;

public record MinecraftServerSaveCmd(
        String id,
        String name,
        String descriptionMarkdown,
        Boolean enabled,
        Integer sort,
        List<Endpoint> endpoints,
        List<Season> seasons
) {

    public record Endpoint(
            String id,
            String name,
            String host,
            Integer port,
            String edition,
            Boolean primaryLine,
            Boolean enabled,
            Integer sort
    ) {
    }

    public record Season(
            String id,
            String name,
            String description,
            Long startedAt,
            Long endedAt,
            Boolean current,
            Integer sort
    ) {
    }
}
