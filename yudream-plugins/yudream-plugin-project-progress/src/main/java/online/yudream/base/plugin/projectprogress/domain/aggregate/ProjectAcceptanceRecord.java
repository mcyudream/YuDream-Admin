package online.yudream.base.plugin.projectprogress.domain.aggregate;

import online.yudream.base.plugin.projectprogress.domain.enumerate.ProjectAcceptanceResult;

import java.util.UUID;

public record ProjectAcceptanceRecord(
        String id,
        String projectId,
        String detailId,
        String operatorUserId,
        ProjectAcceptanceResult result,
        String fromStatusCode,
        String toStatusCode,
        String reason,
        long createdAt
) {

    public ProjectAcceptanceRecord {
        id = requireText(id, "验收记录 ID 不能为空");
        projectId = requireText(projectId, "项目 ID 不能为空");
        detailId = requireText(detailId, "工作细节 ID 不能为空");
        operatorUserId = requireText(operatorUserId, "验收人不能为空");
        if (result == null) {
            throw new IllegalArgumentException("验收结果不能为空");
        }
        fromStatusCode = fromStatusCode == null ? "" : fromStatusCode.trim().toUpperCase();
        toStatusCode = toStatusCode == null ? "" : toStatusCode.trim().toUpperCase();
        reason = reason == null ? "" : reason.trim();
    }

    public static ProjectAcceptanceRecord create(String projectId, String detailId, String operatorUserId,
                                                 ProjectAcceptanceResult result, String fromStatusCode,
                                                 String toStatusCode, String reason) {
        return new ProjectAcceptanceRecord(UUID.randomUUID().toString(), projectId, detailId, operatorUserId, result,
                fromStatusCode, toStatusCode, reason, System.currentTimeMillis());
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
