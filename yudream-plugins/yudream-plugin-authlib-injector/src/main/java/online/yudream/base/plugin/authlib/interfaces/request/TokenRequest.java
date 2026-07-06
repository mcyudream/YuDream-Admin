package online.yudream.base.plugin.authlib.interfaces.request;

public record TokenRequest(
        String accessToken,
        String clientToken
) {
}
