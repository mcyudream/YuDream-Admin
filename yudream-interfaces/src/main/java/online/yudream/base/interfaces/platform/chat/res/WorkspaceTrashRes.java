package online.yudream.base.interfaces.platform.chat.res;

import java.time.LocalDateTime;

public record WorkspaceTrashRes(String id, String path, String kind, long size, int childCount, LocalDateTime deletedAt,
                                LocalDateTime expiresAt) {
}
