package online.yudream.base.plugin.skin.domain.aggregate;

public record SkinTexture(
        String hash,
        String name,
        String type,
        String model,
        String contentType,
        Long size,
        String uploaderId,
        Boolean publicAccess,
        String objectKey,
        Long migratedTid,
        Long uploadedAt
) {
}
