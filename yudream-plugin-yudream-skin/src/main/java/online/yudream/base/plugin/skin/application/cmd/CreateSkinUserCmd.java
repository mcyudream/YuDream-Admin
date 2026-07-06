package online.yudream.base.plugin.skin.application.cmd;

public record CreateSkinUserCmd(
        String email,
        String nickname,
        String password
) {
}
