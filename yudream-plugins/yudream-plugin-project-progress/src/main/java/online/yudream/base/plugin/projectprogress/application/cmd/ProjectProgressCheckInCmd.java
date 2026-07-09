package online.yudream.base.plugin.projectprogress.application.cmd;

import java.util.List;

public record ProjectProgressCheckInCmd(
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
