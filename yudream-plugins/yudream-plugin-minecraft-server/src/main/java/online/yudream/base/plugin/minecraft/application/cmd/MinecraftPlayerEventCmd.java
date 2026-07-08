package online.yudream.base.plugin.minecraft.application.cmd;

public record MinecraftPlayerEventCmd(
        String playerId,
        String playerName,
        Long eventAt
) {
}
