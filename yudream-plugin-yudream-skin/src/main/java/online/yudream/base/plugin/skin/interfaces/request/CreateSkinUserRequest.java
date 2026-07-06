package online.yudream.base.plugin.skin.interfaces.request;

public record CreateSkinUserRequest(
        String email,
        String nickname,
        String password
) {
}
