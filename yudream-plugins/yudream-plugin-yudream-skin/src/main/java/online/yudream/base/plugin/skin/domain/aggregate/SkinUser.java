package online.yudream.base.plugin.skin.domain.aggregate;

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
