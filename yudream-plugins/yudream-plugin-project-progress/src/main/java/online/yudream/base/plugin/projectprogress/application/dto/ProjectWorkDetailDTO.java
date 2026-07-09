package online.yudream.base.plugin.projectprogress.application.dto;

import java.util.List;

public record ProjectWorkDetailDTO(
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
        List<FileEvidenceDTO> acceptanceFiles,
        Long dueAt,
        long createdAt,
        long updatedAt
) {
    public record FileEvidenceDTO(String objectKey, String filename, String contentType, long size, boolean image) {
    }
}
