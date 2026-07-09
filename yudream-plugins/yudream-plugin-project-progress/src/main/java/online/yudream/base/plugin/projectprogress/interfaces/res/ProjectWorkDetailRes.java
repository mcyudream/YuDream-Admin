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
        String acceptanceSummary,
        List<FileEvidenceRes> acceptanceFiles,
        Long dueAt,
        long createdAt,
        long updatedAt
) {
    public record FileEvidenceRes(String objectKey, String filename, String contentType, long size, boolean image) {
    }
}
