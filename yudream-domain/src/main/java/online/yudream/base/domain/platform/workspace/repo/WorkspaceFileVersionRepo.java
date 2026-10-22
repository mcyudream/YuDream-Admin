package online.yudream.base.domain.platform.workspace.repo;

import online.yudream.base.domain.platform.workspace.valobj.WorkspaceFileVersion;

import java.util.List;
import java.util.Optional;

public interface WorkspaceFileVersionRepo {

    Long nextId();

    WorkspaceFileVersion save(WorkspaceFileVersion version);

    Optional<WorkspaceFileVersion> findById(Long id);

    /** 新到旧 */
    List<WorkspaceFileVersion> listByFile(Long fileId);

    void delete(Long id);

    /** 工作区全部版本（含回收站中的文件）占用字节 */
    long usedBytes(Long workspaceId);
}
