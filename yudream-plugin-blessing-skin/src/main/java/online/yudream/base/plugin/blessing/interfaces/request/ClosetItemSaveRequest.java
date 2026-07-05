package online.yudream.base.plugin.blessing.interfaces.request;

public record ClosetItemSaveRequest(
        String userId,
        String textureHash,
        String itemName
) {
}
