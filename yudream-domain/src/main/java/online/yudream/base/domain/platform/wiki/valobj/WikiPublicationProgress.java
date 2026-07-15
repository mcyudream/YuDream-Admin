package online.yudream.base.domain.platform.wiki.valobj;

public record WikiPublicationProgress(Long nodeId, Long versionId, String phase, String message, int percent, boolean completed) {
}
