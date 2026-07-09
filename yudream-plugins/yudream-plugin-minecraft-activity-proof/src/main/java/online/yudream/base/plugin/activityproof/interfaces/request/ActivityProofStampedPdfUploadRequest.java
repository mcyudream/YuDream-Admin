package online.yudream.base.plugin.activityproof.interfaces.request;

public record ActivityProofStampedPdfUploadRequest(
        String filename,
        String contentType,
        String base64
) {
}
