package online.yudream.base.plugin.projectprogress.interfaces.res;

public record ProjectMemberStatsRes(
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
