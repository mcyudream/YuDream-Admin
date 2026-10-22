package online.yudream.base.infra.platform.workspace.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.workspace.enumerate.WorkspaceActor;
import online.yudream.base.domain.platform.workspace.enumerate.WorkspaceEntryKind;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("platformWorkspaceFile")
public class WorkspaceFileDO extends BaseDO {
    private Long workspaceId;
    private String path;
    private WorkspaceEntryKind kind;
    private long size;
    private String sha256;
    private String contentType;
    private int revision;
    private Long currentVersionId;
    private WorkspaceActor updatedBy;
    private LocalDateTime contentUpdatedAt;
    private LocalDateTime deletedAt;
}
