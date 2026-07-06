package online.yudream.base.plugin.authlib.domain.aggregate;

public record ServerJoin(
        String id,
        String serverId,
        String profileId,
        String username,
        String accessToken,
        Long expiresAt
) {
}
