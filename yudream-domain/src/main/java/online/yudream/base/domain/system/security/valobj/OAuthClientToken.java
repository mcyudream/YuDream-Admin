package online.yudream.base.domain.system.security.valobj;

public record OAuthClientToken(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        String scope
) {
}
