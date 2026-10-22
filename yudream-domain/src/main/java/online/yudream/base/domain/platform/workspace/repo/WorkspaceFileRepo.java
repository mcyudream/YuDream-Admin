package online.yudream.base.domain.platform.workspace.repo;

import online.yudream.base.domain.platform.workspace.aggregate.WorkspaceFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WorkspaceFileRepo {

    /** 未删除的全部条目，按路径排序 */
    List<WorkspaceFile> listActive(Long workspaceId);

    List<WorkspaceFile> listTrash(Long workspaceId);

    Optional<WorkspaceFile> findActive(Long workspaceId, String path);

    Optional<WorkspaceFile> findById(Long id);

    /** 乐观锁保存：并发修改时抛出冲突异常 */
    WorkspaceFile save(WorkspaceFile file);

    void delete(Long id);

    /** 全部工作区中回收站删除早于 cutoff 的条目（清理任务分批） */
    List<WorkspaceFile> findTrashedBefore(LocalDateTime cutoff, int limit);

    /** 全部工作区中路径位于 directory 下、内容最后修改早于 cutoff 的未删除文件 */
    List<WorkspaceFile> findStaleFilesUnder(String directory, LocalDateTime cutoff, int limit);
}
