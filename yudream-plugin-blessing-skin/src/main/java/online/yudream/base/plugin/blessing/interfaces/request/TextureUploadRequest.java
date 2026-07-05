package online.yudream.base.plugin.blessing.interfaces.request;

public record TextureUploadRequest(
        String name,
        String type,
        String model,
        String contentType,
        String base64,
        Boolean publicAccess
) {
}
