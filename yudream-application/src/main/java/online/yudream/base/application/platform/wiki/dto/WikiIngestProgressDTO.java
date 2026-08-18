package online.yudream.base.application.platform.wiki.dto;

public record WikiIngestProgressDTO(String taskId, String spaceId, String sourceId, String phase,
                                    String message, int percent, boolean completed) {
}
