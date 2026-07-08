package online.yudream.base.plugin.minecraft.application.dto;

public record MinecraftEndpointStatusDTO(
        String endpointId,
        String status,
        int onlinePlayers,
        int maxPlayers,
        String versionName,
        Integer protocolId,
        Long ping,
        String motd,
        String errorMessage,
        long checkedAt
) {
}
