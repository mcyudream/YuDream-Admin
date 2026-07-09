package online.yudream.base.plugin.projectprogress.interfaces.res;

public record ProjectMinecraftServerOptionRes(
        String id,
        String name,
        boolean enabled,
        String currentSeasonId,
        String currentSeasonName
) {
}
