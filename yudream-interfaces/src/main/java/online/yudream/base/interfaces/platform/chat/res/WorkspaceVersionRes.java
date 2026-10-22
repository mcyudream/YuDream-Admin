package online.yudream.base.interfaces.platform.chat.res;

import java.time.LocalDateTime;

public record WorkspaceVersionRes(String id, int revision, long size, String actor, LocalDateTime createdAt, boolean current) {
}
