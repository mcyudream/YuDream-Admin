package online.yudream.base.interfaces.platform.wiki.res;

import lombok.Builder;

@Builder
public record WikiIngestProgressRes(String event, String action, String module, String taskId, String spaceId,
                                    String sourceId, String phase, String message, int percent,
                                    boolean completed, long timestamp) {
}
