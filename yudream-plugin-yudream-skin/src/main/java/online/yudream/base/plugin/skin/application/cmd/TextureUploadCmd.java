package online.yudream.base.plugin.skin.application.cmd;

public record TextureUploadCmd(
        String name,
        String type,
        String model,
        String contentType,
        String base64,
        Boolean publicAccess
) {
}
