package online.yudream.base.plugin.projectprogress.application.dto;

public record ProjectAcceptanceDTO(
        String id,
        String projectId,
        String detailId,
        String operatorUserId,
        String result,
        String fromStatusCode,
        String toStatusCode,
        String reason,
        long createdAt
) {
}
