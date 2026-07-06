package online.yudream.base.plugin.skin.interfaces.request;

public record ClosetItemSaveRequest(
        String userId,
        String textureHash,
        String itemName
) {
}
