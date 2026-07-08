package online.yudream.base.plugin.activityproof.application.dto;

public record ActivityProofSettingsDTO(
        boolean templateReady,
        String templateId,
        String templateCode,
        String templateName,
        String templateFilename,
        long templateUpdatedAt,
        String defaultActivityName,
        String defaultCollege,
        String defaultIssuer,
        long updatedAt
) {
}
