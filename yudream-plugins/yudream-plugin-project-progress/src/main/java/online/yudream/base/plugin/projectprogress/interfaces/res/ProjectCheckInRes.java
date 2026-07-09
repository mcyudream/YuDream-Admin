package online.yudream.base.plugin.projectprogress.interfaces.res;

import java.util.List;

public record ProjectCheckInRes(
        String id,
        String projectId,
        String detailId,
        String userId,
        String type,
        String summary,
        List<FileEvidenceRes> files,
        LocationRes location,
        MinecraftEvidenceRes minecraft,
        long createdAt
) {
    public record FileEvidenceRes(String objectKey, String filename, String contentType, long size, boolean image) {
    }

    public record LocationRes(String address, Double latitude, Double longitude) {
    }

    public record MinecraftEvidenceRes(String serverId, String playerId, String playerName,
                                       long totalOnlineMillis, long totalAfkMillis, long effectiveOnlineMillis) {
    }
}
