package online.yudream.base.plugin.blessing.domain.aggregate;

public record SkinClosetItem(
        String id,
        String userId,
        String textureHash,
        String itemName,
        Long createdAt
) {
}
