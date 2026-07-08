package online.yudream.base.plugin.activityproof.application.dto;

public record ActivityProofExportDTO(
        String id,
        String serverId,
        String serverName,
        String activityName,
        String outputFilename,
        String downloadPath,
        int participantCount,
        int unmatchedCount,
        String operatorUserId,
        long generatedAt
) {
}
