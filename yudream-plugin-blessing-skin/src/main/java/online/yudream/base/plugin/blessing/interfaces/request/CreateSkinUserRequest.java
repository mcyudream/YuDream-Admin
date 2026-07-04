package online.yudream.base.plugin.blessing.interfaces.request;

public record CreateSkinUserRequest(
        String email,
        String nickname,
        String password
) {
}
