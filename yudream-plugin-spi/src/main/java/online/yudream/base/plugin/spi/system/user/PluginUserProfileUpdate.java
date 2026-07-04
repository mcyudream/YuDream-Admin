package online.yudream.base.plugin.spi.system.user;

public record PluginUserProfileUpdate(
        String nickname,
        String email,
        String phone,
        String qq,
        String avatar
) {
}
