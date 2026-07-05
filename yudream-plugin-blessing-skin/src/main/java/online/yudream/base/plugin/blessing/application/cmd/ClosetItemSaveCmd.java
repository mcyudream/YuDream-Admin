package online.yudream.base.plugin.blessing.application.cmd;

public record ClosetItemSaveCmd(
        String userId,
        String textureHash,
        String itemName
) {
}
