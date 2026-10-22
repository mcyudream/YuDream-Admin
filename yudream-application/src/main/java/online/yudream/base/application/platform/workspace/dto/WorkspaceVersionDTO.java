package online.yudream.base.application.platform.workspace.dto;

import java.time.LocalDateTime;

public record WorkspaceVersionDTO(String id, int revision, long size, String actor, LocalDateTime createdAt, boolean current) {
}
