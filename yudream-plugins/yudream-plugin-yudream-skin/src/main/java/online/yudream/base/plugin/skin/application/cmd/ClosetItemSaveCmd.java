package online.yudream.base.plugin.skin.application.cmd;

public record ClosetItemSaveCmd(
        String userId,
        String textureHash,
        String itemName
) {
}
