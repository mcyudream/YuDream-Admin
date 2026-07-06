package online.yudream.base.plugin.spi.system.user;

public record PluginUserCreate(
        String username,
        String nickname,
        String email,
        String phone,
        String qq,
        String password,
        String encodedPassword,
        boolean emailVerified
) {
}
