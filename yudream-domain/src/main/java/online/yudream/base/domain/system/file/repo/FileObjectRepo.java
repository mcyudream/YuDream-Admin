package online.yudream.base.domain.system.file.repo;

import online.yudream.base.domain.system.file.aggregate.FileObject;

import java.util.Optional;

public interface FileObjectRepo {

    FileObject save(FileObject fileObject);

    Optional<FileObject> findById(Long id);
}
