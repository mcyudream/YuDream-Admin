package online.yudream.base.plugin.skin.interfaces.request;

public record TextureUploadRequest(
        String name,
        String type,
        String model,
        String contentType,
        String base64,
        Boolean publicAccess
) {
}
