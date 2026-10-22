package online.yudream.base.infra.platform.workspace.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.platform.workspace.repo.WorkspaceFileVersionRepo;
import online.yudream.base.domain.platform.workspace.valobj.WorkspaceFileVersion;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.workspace.dataobj.WorkspaceFileVersionDO;
import online.yudream.base.infra.platform.workspace.mapper.WorkspaceInfraMapper;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class WorkspaceFileVersionRepoImpl implements WorkspaceFileVersionRepo {

    private final MongoTemplate mongo;
    private final IdGenerator ids;

    @PostConstruct
    void ensureIndexes() {
        try {
            var index = mongo.indexOps(WorkspaceFileVersionDO.class);
            index.ensureIndex(new Index().on("fileId", Sort.Direction.ASC).on("revision", Sort.Direction.DESC));
            index.ensureIndex(new Index().on("workspaceId", Sort.Direction.ASC));
        } catch (RuntimeException e) {
            log.warn("工作区文件版本索引创建失败：{}", e.getMessage());
        }
    }

    @Override
    public Long nextId() {
        return ids.nextId();
    }

    @Override
    public WorkspaceFileVersion save(WorkspaceFileVersion version) {
        return WorkspaceInfraMapper.version(mongo.insert(WorkspaceInfraMapper.version(version)));
    }

    @Override
    public Optional<WorkspaceFileVersion> findById(Long id) {
        return Optional.ofNullable(WorkspaceInfraMapper.version(mongo.findById(id, WorkspaceFileVersionDO.class)));
    }

    @Override
    public List<WorkspaceFileVersion> listByFile(Long fileId) {
        Query query = Query.query(Criteria.where("fileId").is(fileId)).with(Sort.by(Sort.Direction.DESC, "revision"));
        return mongo.find(query, WorkspaceFileVersionDO.class).stream().map(WorkspaceInfraMapper::version).toList();
    }

    @Override
    public void delete(Long id) {
        mongo.remove(Query.query(Criteria.where("id").is(id)), WorkspaceFileVersionDO.class);
    }

    @Override
    public long usedBytes(Long workspaceId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("workspaceId").is(workspaceId)),
                Aggregation.group().sum("size").as("total"));
        Document result = mongo.aggregate(aggregation, WorkspaceFileVersionDO.class, Document.class).getUniqueMappedResult();
        Object total = result == null ? null : result.get("total");
        return total instanceof Number number ? number.longValue() : 0L;
    }
}
