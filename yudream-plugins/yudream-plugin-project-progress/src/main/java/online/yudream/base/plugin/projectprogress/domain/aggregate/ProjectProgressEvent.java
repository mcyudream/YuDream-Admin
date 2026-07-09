package online.yudream.base.plugin.projectprogress.domain.aggregate;

import online.yudream.base.plugin.projectprogress.domain.enumerate.ProjectProgressEventType;

import java.util.Map;
import java.util.UUID;

public record ProjectProgressEvent(
        String id,
        String projectId,
        String detailId,
        String operatorUserId,
        ProjectProgressEventType type,
        String message,
        Map<String, Object> metadata,
        long createdAt
) {

    public ProjectProgressEvent {
        id = requireText(id, "事件 ID 不能为空");
        projectId = requireText(projectId, "项目 ID 不能为空");
        detailId = detailId == null ? "" : detailId.trim();
        operatorUserId = operatorUserId == null ? "" : operatorUserId.trim();
        if (type == null) {
            throw new IllegalArgumentException("事件类型不能为空");
        }
        message = message == null ? "" : message.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ProjectProgressEvent create(String projectId, String detailId, String operatorUserId,
                                              ProjectProgressEventType type, String message, Map<String, Object> metadata) {
        return new ProjectProgressEvent(UUID.randomUUID().toString(), projectId, detailId, operatorUserId, type,
                message, metadata, System.currentTimeMillis());
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
