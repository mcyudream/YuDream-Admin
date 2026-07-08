package online.yudream.base.plugin.activityproof.application.dto;

public record ActivityProofTemplateDTO(
        String id,
        String code,
        String name,
        String originalFilename,
        long updatedAt
) {
}
