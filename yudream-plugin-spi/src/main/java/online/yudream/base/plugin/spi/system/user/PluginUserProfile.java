package online.yudream.base.plugin.spi.system.user;

public record PluginUserProfile(
        Long id,
        String username,
        String nickname,
        String email,
        String phone,
        String qq,
        String avatar,
        String status
) {
}
