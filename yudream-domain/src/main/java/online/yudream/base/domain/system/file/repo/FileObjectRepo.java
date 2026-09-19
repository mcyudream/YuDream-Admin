package online.yudream.base.domain.system.file.repo;

import online.yudream.base.domain.system.file.aggregate.FileObject;

import java.util.List;
import java.util.Optional;

public interface FileObjectRepo {

    FileObject save(FileObject fileObject);

    Optional<FileObject> findById(Long id);

    List<FileObject> page(String keyword, String module, Boolean publicAccess, int page, int size);

    long count(String keyword, String module, Boolean publicAccess);

    /**
     * 按属主过滤的分页查询：uploaderId 非空时仅返回该用户上传的文件；
     * includePublic 为 true 时并集公开文件（publicAccess=true）。
     */
    List<FileObject> page(String keyword, String module, Boolean publicAccess, Long uploaderId,
                          boolean includePublic, int page, int size);

    long count(String keyword, String module, Boolean publicAccess, Long uploaderId, boolean includePublic);
}
