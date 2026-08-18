package online.yudream.base.application.platform.wiki.dto;

import java.time.LocalDateTime;

public record WikiIngestTaskDTO(
        String id,
        String spaceId,
        String sourceId,
        String taskType,
        String status,
        int attempts,
        int maxAttempts,
        String errorMessage,
        String phase,
        int percent,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long sortOrder,
        String payloadJson
) {
}
