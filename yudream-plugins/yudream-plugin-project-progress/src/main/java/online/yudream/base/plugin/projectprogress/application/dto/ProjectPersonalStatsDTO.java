package online.yudream.base.plugin.projectprogress.application.dto;

public record ProjectPersonalStatsDTO(
        String userId,
        int assignedDetails,
        int completedDetails,
        int pendingAcceptanceDetails,
        int acceptedReviews,
        int rejectedReviews,
        int checkIns
) {
}
