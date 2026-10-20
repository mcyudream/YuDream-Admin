package online.yudream.base.infra.platform.cms.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.cms.aggregate.StudioBlockTemplate;
import online.yudream.base.domain.platform.cms.repo.StudioBlockTemplateRepo;
import online.yudream.base.infra.platform.cms.dataobj.StudioBlockTemplateDO;
import online.yudream.base.infra.platform.cms.mapper.StudioBlockTemplateInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StudioBlockTemplateRepoImpl implements StudioBlockTemplateRepo {
    private final MongoTemplate mongoTemplate;

    @Override
    public List<StudioBlockTemplate> findAll() {
        return mongoTemplate.findAll(StudioBlockTemplateDO.class).stream().map(StudioBlockTemplateInfraMapper::toDomain).toList();
    }

    @Override
    public Optional<StudioBlockTemplate> findById(String id) {
        return Optional.ofNullable(StudioBlockTemplateInfraMapper.toDomain(mongoTemplate.findById(id, StudioBlockTemplateDO.class)));
    }

    @Override
    public long count() {
        return mongoTemplate.count(new Query(), StudioBlockTemplateDO.class);
    }

    @Override
    public void insert(StudioBlockTemplate template) {
        mongoTemplate.insert(StudioBlockTemplateInfraMapper.toData(template));
    }

    @Override
    public boolean compareAndSet(long expectedRevision, StudioBlockTemplate template) {
        Query query = Query.query(Criteria.where("_id").is(template.id()).and("revision").is(expectedRevision));
        StudioBlockTemplateDO replaced = mongoTemplate.findAndReplace(query, StudioBlockTemplateInfraMapper.toData(template));
        return replaced != null;
    }

    @Override
    public boolean delete(String id) {
        return mongoTemplate.remove(Query.query(Criteria.where("_id").is(id)), StudioBlockTemplateDO.class).getDeletedCount() > 0;
    }
}
