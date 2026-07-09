package online.yudream.base.plugin.projectprogress.interfaces.res;

import java.util.List;

public record ProjectWorkDetailRes(
        String id,
        String projectId,
        String title,
        String description,
        String statusCode,
        String assignmentMode,
        int requiredAssigneeCount,
        List<String> candidateUserIds,
        List<String> assigneeUserIds,
        List<String> acceptorUserIds,
        boolean published,
        boolean pendingAcceptance,
        Long dueAt,
        long createdAt,
        long updatedAt
) {
}
