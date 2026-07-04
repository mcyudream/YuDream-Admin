package online.yudream.base.plugin.blessing.domain.aggregate;

public record SkinUser(
        String id,
        String email,
        String emailLower,
        String nickname,
        String passwordHash,
        Long migratedUid,
        Long createdAt
) {
}
