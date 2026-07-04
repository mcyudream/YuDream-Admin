package online.yudream.base.domain.system.file.repo;

import online.yudream.base.domain.system.file.aggregate.FileObject;

import java.util.List;
import java.util.Optional;

public interface FileObjectRepo {

    FileObject save(FileObject fileObject);

    Optional<FileObject> findById(Long id);

    List<FileObject> page(String keyword, String module, Boolean publicAccess, int page, int size);

    long count(String keyword, String module, Boolean publicAccess);
}
