package online.yudream.base.interfaces.platform.chat.res;

import java.time.LocalDateTime;

public record WorkspaceEntryRes(String id, String path, String kind, long size, String contentType, int revision,
                                String updatedBy, LocalDateTime updatedAt) {
}
