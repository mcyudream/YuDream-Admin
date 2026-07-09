package online.yudream.base.plugin.projectprogress.interfaces.request;

import java.util.List;

public record ProjectProgressCheckInRequest(
        String type,
        String summary,
        List<FileEvidence> files,
        Location location
) {
    public record FileEvidence(String filename, String contentType, String base64, Boolean image) {
    }

    public record Location(String address, Double latitude, Double longitude) {
    }
}
