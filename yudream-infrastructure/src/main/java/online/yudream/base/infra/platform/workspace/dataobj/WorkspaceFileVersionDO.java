package online.yudream.base.infra.platform.workspace.dataobj;

import lombok.Data;
import online.yudream.base.domain.platform.workspace.enumerate.WorkspaceActor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** 不可变内容版本行：只插入与删除，不更新。 */
@Data
@Document("platformWorkspaceFileVersion")
public class WorkspaceFileVersionDO {
    @Id
    private Long id;
    private Long workspaceId;
    private Long fileId;
    private int revision;
    private String objectKey;
    private long size;
    private String sha256;
    private String contentType;
    private WorkspaceActor actor;
    private LocalDateTime createdAt;
}
