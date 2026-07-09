package online.yudream.base.plugin.activityproof.domain.aggregate;

public record ActivityProofParticipantSnapshot(
        String userId,
        String studentName,
        String studentNo,
        String className,
        String college,
        String playerId,
        String playerName
) {
    public ActivityProofParticipantSnapshot {
        userId = text(userId);
        studentName = text(studentName);
        studentNo = text(studentNo);
        className = text(className);
        college = text(college);
        playerId = text(playerId);
        playerName = text(playerName);
    }

    public boolean belongsTo(String targetUserId, String targetStudentNo) {
        String safeUserId = text(targetUserId);
        String safeStudentNo = text(targetStudentNo);
        return (!safeUserId.isBlank() && safeUserId.equals(userId))
                || (!safeStudentNo.isBlank() && safeStudentNo.equalsIgnoreCase(studentNo));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
