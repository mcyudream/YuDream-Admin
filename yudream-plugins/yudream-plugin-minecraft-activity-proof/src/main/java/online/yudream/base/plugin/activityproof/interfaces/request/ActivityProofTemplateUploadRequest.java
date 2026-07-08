package online.yudream.base.plugin.activityproof.interfaces.request;

public record ActivityProofTemplateUploadRequest(
        String filename,
        String contentType,
        String contentBase64
) {
}
