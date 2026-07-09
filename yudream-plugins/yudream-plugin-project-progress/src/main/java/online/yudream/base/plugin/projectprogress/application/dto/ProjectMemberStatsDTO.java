package online.yudream.base.plugin.projectprogress.application.dto;

public record ProjectMemberStatsDTO(
        String projectId,
        String userId,
        int assignedDetails,
        int completedDetails,
        int pendingAcceptanceDetails,
        int acceptedReviews,
        int rejectedReviews,
        int checkIns,
        long lastActivityAt
) {
}
