package online.yudream.base.plugin.activityproof.application.cmd;

public record ActivityProofTemplateUploadCmd(
        String filename,
        String contentType,
        String contentBase64
) {
}
