package online.yudream.base.plugin.projectprogress.interfaces.res;

public record ProjectAcceptanceRes(
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
