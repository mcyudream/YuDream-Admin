package online.yudream.base.plugin.authlib.interfaces.request;

public record JoinRequest(
        String accessToken,
        String selectedProfile,
        String serverId
) {
}
