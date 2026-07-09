package online.yudream.base.plugin.activityproof.application.cmd;

public record ActivityProofStampedPdfUploadCmd(
        String id,
        String filename,
        String contentType,
        String base64
) {
}
