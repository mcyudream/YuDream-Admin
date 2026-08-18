package online.yudream.base.application.platform.wiki.dto;

public record WikiPublicationProgressDTO(String nodeId, String versionId, String phase,
                                         String message, int percent, boolean completed) {
}
