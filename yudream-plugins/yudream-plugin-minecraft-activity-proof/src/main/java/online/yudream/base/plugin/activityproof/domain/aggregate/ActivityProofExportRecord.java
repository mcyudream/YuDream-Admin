package online.yudream.base.plugin.activityproof.domain.aggregate;

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
        long generatedAt
) {
    public static ActivityProofExportRecord create(String serverId, String serverName, String activityName,
                                                   String outputObjectKey, String outputFilename,
                                                   int participantCount, int unmatchedCount, String operatorUserId) {
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
                System.currentTimeMillis()
        );
    }
}
