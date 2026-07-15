package online.yudream.base.interfaces.platform.wiki.res;

import lombok.Builder;

@Builder
public record WikiPublicationProgressRes(String event, String action, String module, String nodeId, String versionId,
                                         String phase, String message, int percent, boolean completed, long timestamp) {
}
