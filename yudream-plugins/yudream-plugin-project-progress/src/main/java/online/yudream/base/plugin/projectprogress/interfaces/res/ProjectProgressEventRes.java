package online.yudream.base.plugin.projectprogress.interfaces.res;

import java.util.Map;

public record ProjectProgressEventRes(
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
