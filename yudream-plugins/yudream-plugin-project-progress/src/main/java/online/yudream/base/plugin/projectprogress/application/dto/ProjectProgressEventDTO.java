package online.yudream.base.plugin.projectprogress.application.dto;

import java.util.Map;

public record ProjectProgressEventDTO(
        String id,
        String projectId,
        String detailId,
        String operatorUserId,
        String type,
        String message,
        Map<String, Object> metadata,
        long createdAt
) {
}
