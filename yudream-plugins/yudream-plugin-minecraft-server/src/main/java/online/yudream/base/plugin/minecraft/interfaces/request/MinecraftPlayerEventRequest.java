package online.yudream.base.plugin.minecraft.interfaces.request;

public record MinecraftPlayerEventRequest(
        String playerId,
        String uuid,
        String playerName,
        String name,
        Long eventAt
) {
}
