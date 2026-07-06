package online.yudream.base.plugin.authlib.domain.aggregate;

public record AuthSession(
        String accessToken,
        String clientToken,
        String userId,
        String username,
        String selectedProfileId,
        Long issuedAt,
        Long expiresAt
) {
}
