package online.yudream.base.application.platform.workspace.dto;

import java.time.LocalDateTime;

/** 回收站条目（只列删除时的顶层条目；目录连同其内容一起恢复）。 */
public record WorkspaceTrashDTO(String id, String path, String kind, long size, int childCount, LocalDateTime deletedAt,
                                LocalDateTime expiresAt) {
}
