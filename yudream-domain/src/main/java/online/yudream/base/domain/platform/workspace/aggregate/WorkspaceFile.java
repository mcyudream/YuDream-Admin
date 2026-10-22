package online.yudream.base.domain.platform.workspace.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.workspace.enumerate.WorkspaceActor;
import online.yudream.base.domain.platform.workspace.enumerate.WorkspaceEntryKind;
import online.yudream.base.domain.platform.workspace.valobj.WorkspaceFileVersion;

import java.time.LocalDateTime;

/**
 * 工作区条目（文件或目录）的元数据：路径是工作区内相对路径；文件指向当前内容版本。
 * 改名/移动只改路径；删除进入回收站（deletedAt），到期后连同全部版本内容清除。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WorkspaceFile extends BaseDomain {

    private Long workspaceId;
    private String path;
    private WorkspaceEntryKind kind;
    private long size;
    private String sha256;
    private String contentType;
    /** 内容修订号：每次写入 +1，界面保存时用作乐观并发校验 */
    private int revision;
    private Long currentVersionId;
    private WorkspaceActor updatedBy;
    private LocalDateTime contentUpdatedAt;
    private LocalDateTime deletedAt;

    public static WorkspaceFile file(Long workspaceId, String path) {
        return WorkspaceFile.builder().workspaceId(required(workspaceId)).path(path).kind(WorkspaceEntryKind.FILE).build();
    }

    public static WorkspaceFile directory(Long workspaceId, String path, WorkspaceActor actor) {
        return WorkspaceFile.builder().workspaceId(required(workspaceId)).path(path).kind(WorkspaceEntryKind.DIRECTORY)
                .updatedBy(actor).contentUpdatedAt(LocalDateTime.now()).build();
    }

    public boolean directory() {
        return kind == WorkspaceEntryKind.DIRECTORY;
    }

    public boolean trashed() {
        return deletedAt != null;
    }

    /** 写入新内容版本 */
    public void recordVersion(WorkspaceFileVersion version) {
        if (directory()) {
            throw new BizException("目录不能写入内容：" + path);
        }
        currentVersionId = version.id();
        revision = version.revision();
        size = version.size();
        sha256 = version.sha256();
        contentType = version.contentType();
        updatedBy = version.actor();
        contentUpdatedAt = version.createdAt();
    }

    public void moveTo(String target, WorkspaceActor actor) {
        path = target;
        updatedBy = actor;
        contentUpdatedAt = LocalDateTime.now();
    }

    public void trash(LocalDateTime at) {
        deletedAt = at;
    }

    public void restoreAs(String target) {
        deletedAt = null;
        path = target;
    }

    /** 自身或位于 directory 目录内 */
    public boolean within(String directory) {
        return path.equals(directory) || path.startsWith(directory + "/");
    }

    private static Long required(Long workspaceId) {
        if (workspaceId == null) {
            throw new BizException("工作区不能为空");
        }
        return workspaceId;
    }
}
