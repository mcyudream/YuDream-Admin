package online.yudream.base.interfaces.platform.wiki.res;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record WikiIngestTaskRes(
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
