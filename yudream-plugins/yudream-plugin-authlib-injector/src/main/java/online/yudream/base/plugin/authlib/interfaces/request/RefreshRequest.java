package online.yudream.base.plugin.authlib.interfaces.request;

public record RefreshRequest(
        String accessToken,
        String clientToken,
        SelectedProfile selectedProfile,
        Boolean requestUser
) {
    public record SelectedProfile(String id, String name) {
    }
}
