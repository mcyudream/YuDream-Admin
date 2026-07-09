package online.yudream.base.plugin.activityproof.domain.aggregate;

import java.util.List;
import java.util.UUID;

public record ActivityProofExportRecord(
        String id,
        String serverId,
        String serverName,
        String activityName,
        String outputObjectKey,
        String outputFilename,
        int participantCount,
        int unmatchedCount,
        String operatorUserId,
        long generatedAt,
        String stampedPdfObjectKey,
        String stampedPdfFilename,
        String stampedPdfContentType,
        long stampedPdfSize,
        long stampedPdfUploadedAt,
        List<ActivityProofParticipantSnapshot> participants
) {
    public ActivityProofExportRecord {
        participants = participants == null ? List.of() : List.copyOf(participants);
    }

    public static ActivityProofExportRecord create(String serverId, String serverName, String activityName,
                                                   String outputObjectKey, String outputFilename,
                                                   int participantCount, int unmatchedCount, String operatorUserId,
                                                   List<ActivityProofParticipantSnapshot> participants) {
        return new ActivityProofExportRecord(
                UUID.randomUUID().toString(),
                serverId,
                serverName,
                activityName,
                outputObjectKey,
                outputFilename,
                participantCount,
                unmatchedCount,
                operatorUserId,
                System.currentTimeMillis(),
                "",
                "",
                "",
                0,
                0,
                participants
        );
    }

    public ActivityProofExportRecord withStampedPdf(String objectKey, String filename, String contentType, long size, long uploadedAt) {
        return new ActivityProofExportRecord(
                id,
                serverId,
                serverName,
                activityName,
                outputObjectKey,
                outputFilename,
                participantCount,
                unmatchedCount,
                operatorUserId,
                generatedAt,
                text(objectKey),
                text(filename),
                text(contentType),
                size,
                uploadedAt,
                participants
        );
    }

    public boolean hasStampedPdf() {
        return !text(stampedPdfObjectKey).isBlank();
    }

    public boolean containsParticipant(String userId, String studentNo) {
        return participants.stream().anyMatch(participant -> participant.belongsTo(userId, studentNo));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
