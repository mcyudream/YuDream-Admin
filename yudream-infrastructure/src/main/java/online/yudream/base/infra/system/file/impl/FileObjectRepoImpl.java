package online.yudream.base.infra.system.file.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.file.aggregate.FileObject;
import online.yudream.base.domain.system.file.repo.FileObjectRepo;
import online.yudream.base.infra.system.file.dataobj.FileObjectDO;
import online.yudream.base.infra.system.file.mapper.FileObjectInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FileObjectRepoImpl implements FileObjectRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public FileObject save(FileObject fileObject) {
        FileObjectDO fileObjectDO = FileObjectInfraMapper.toDataObj(fileObject);
        if (fileObjectDO.getId() == null) {
            fileObjectDO.setId(idGenerator.nextId());
            fileObjectDO.setCreateTime(LocalDateTime.now());
        }
        fileObjectDO.setUpdateTime(LocalDateTime.now());
        FileObjectDO saved = mongoTemplate.save(fileObjectDO);
        return FileObjectInfraMapper.toDomain(saved);
    }

    @Override
    public Optional<FileObject> findById(Long id) {
        return Optional.ofNullable(FileObjectInfraMapper.toDomain(mongoTemplate.findById(id, FileObjectDO.class)));
    }
}
