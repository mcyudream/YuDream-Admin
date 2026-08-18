package online.yudream.base.infra.platform.wiki.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.wiki.aggregate.WikiIngestTask;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestTaskStatus;
import online.yudream.base.domain.platform.wiki.repo.WikiIngestTaskRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.wiki.dataobj.WikiIngestTaskDO;
import online.yudream.base.infra.platform.wiki.mapper.WikiKnowledgeInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WikiIngestTaskRepoImpl implements WikiIngestTaskRepo {

    private final MongoTemplate mongo;
    private final IdGenerator ids;

    @Override
    public WikiIngestTask save(WikiIngestTask task) {
        WikiIngestTaskDO dataObj = WikiKnowledgeInfraMapper.ingestTask(task);
        if (dataObj.getId() == null) {
            dataObj.setId(ids.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        WikiIngestTaskDO saved = mongo.save(dataObj);
        task.setVersion(saved.getVersion());
        return WikiKnowledgeInfraMapper.ingestTask(saved);
    }

    @Override
    public Optional<WikiIngestTask> findById(Long id) {
        return Optional.ofNullable(WikiKnowledgeInfraMapper.ingestTask(mongo.findById(id, WikiIngestTaskDO.class)));
    }

    @Override
    public Optional<WikiIngestTask> findNextQueued() {
        return Optional.ofNullable(WikiKnowledgeInfraMapper.ingestTask(mongo.findOne(
                Query.query(Criteria.where("status").is(WikiIngestTaskStatus.QUEUED))
                        .with(Sort.by(Sort.Direction.ASC, "sortOrder", "createTime")),
                WikiIngestTaskDO.class)));
    }

    @Override
    public List<WikiIngestTask> findBySpaceId(Long spaceId) {
        return mongo.find(Query.query(Criteria.where("spaceId").is(spaceId))
                        .with(Sort.by(Sort.Direction.DESC, "createTime")),
                WikiIngestTaskDO.class).stream().map(WikiKnowledgeInfraMapper::ingestTask).toList();
    }

    @Override
    public List<WikiIngestTask> findByStatus(WikiIngestTaskStatus status) {
        return mongo.find(Query.query(Criteria.where("status").is(status)), WikiIngestTaskDO.class).stream()
                .map(WikiKnowledgeInfraMapper::ingestTask).toList();
    }

    @Override
    public void deleteById(Long id) {
        mongo.remove(Query.query(Criteria.where("id").is(id)), WikiIngestTaskDO.class);
    }
}
