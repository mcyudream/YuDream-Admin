package online.yudream.base.infra.system.file.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.file.aggregate.FileObject;
import online.yudream.base.domain.system.file.repo.FileObjectRepo;
import online.yudream.base.infra.system.file.dataobj.FileObjectDO;
import online.yudream.base.infra.system.file.mapper.FileObjectInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

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

    @Override
    public List<FileObject> page(String keyword, String module, Boolean publicAccess, int page, int size) {
        Query query = buildPageQuery(keyword, module, publicAccess)
                .with(Sort.by(Sort.Direction.DESC, "createTime"));
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return mongoTemplate.find(query, FileObjectDO.class).stream()
                .map(FileObjectInfraMapper::toDomain)
                .toList();
    }

    @Override
    public long count(String keyword, String module, Boolean publicAccess) {
        return mongoTemplate.count(buildPageQuery(keyword, module, publicAccess), FileObjectDO.class);
    }

    private Query buildPageQuery(String keyword, String module, Boolean publicAccess) {
        List<Criteria> criteriaList = new ArrayList<>();
        criteriaList.add(new Criteria().orOperator(
                Criteria.where("deleted").is(false),
                Criteria.where("deleted").exists(false)
        ));
        if (StringUtils.hasText(module)) {
            criteriaList.add(Criteria.where("module").is(module.trim()));
        }
        if (publicAccess != null) {
            criteriaList.add(Criteria.where("publicAccess").is(publicAccess));
        }
        if (StringUtils.hasText(keyword)) {
            String pattern = ".*" + Pattern.quote(keyword.trim()) + ".*";
            criteriaList.add(new Criteria().orOperator(
                    Criteria.where("originalName").regex(pattern, "i"),
                    Criteria.where("contentType").regex(pattern, "i"),
                    Criteria.where("objectKey").regex(pattern, "i")
            ));
        }
        return Query.query(new Criteria().andOperator(criteriaList));
    }
}
