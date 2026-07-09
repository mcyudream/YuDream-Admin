package online.yudream.base.plugin.projectprogress.interfaces.res;

public record ProjectPersonalStatsRes(
        String userId,
        int assignedDetails,
        int completedDetails,
        int pendingAcceptanceDetails,
        int acceptedReviews,
        int rejectedReviews,
        int checkIns
) {
}
