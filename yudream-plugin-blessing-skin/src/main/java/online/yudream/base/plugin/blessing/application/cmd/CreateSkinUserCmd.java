package online.yudream.base.plugin.blessing.application.cmd;

public record CreateSkinUserCmd(
        String email,
        String nickname,
        String password
) {
}
