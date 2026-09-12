package online.yudream.base.infra.platform.plugin.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketPublication;
import online.yudream.base.domain.platform.plugin.enumerate.PluginPublicationStatus;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketPublicationRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.plugin.dataobj.PluginMarketPublicationDO;
import online.yudream.base.infra.platform.plugin.mapper.PluginMarketPublicationInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PluginMarketPublicationRepoImpl implements PluginMarketPublicationRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public PluginMarketPublication save(PluginMarketPublication publication) {
        PluginMarketPublicationDO dataObj = PluginMarketPublicationInfraMapper.toDataObj(publication);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        PluginMarketPublicationDO saved = mongoTemplate.save(dataObj);
        publication.setVersion(saved.getVersion());
        return PluginMarketPublicationInfraMapper.toDomain(saved);
    }

    @Override
    public Optional<PluginMarketPublication> findById(Long id) {
        return Optional.ofNullable(PluginMarketPublicationInfraMapper.toDomain(
                mongoTemplate.findById(id, PluginMarketPublicationDO.class)));
    }

    @Override
    public Optional<PluginMarketPublication> findByCodeAndVersion(String code, String pluginVersion) {
        Query query = Query.query(Criteria.where("code").is(code).and("pluginVersion").is(pluginVersion));
        return Optional.ofNullable(PluginMarketPublicationInfraMapper.toDomain(
                mongoTemplate.findOne(query, PluginMarketPublicationDO.class)));
    }

    @Override
    public List<PluginMarketPublication> findByStatus(PluginPublicationStatus status) {
        Query query = Query.query(Criteria.where("status").is(status))
                .with(Sort.by(Sort.Direction.DESC, "createTime"));
        return mongoTemplate.find(query, PluginMarketPublicationDO.class).stream()
                .map(PluginMarketPublicationInfraMapper::toDomain)
                .toList();
    }

    @Override
    public List<PluginMarketPublication> findAll() {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "createTime"));
        return mongoTemplate.find(query, PluginMarketPublicationDO.class).stream()
                .map(PluginMarketPublicationInfraMapper::toDomain)
                .toList();
    }

    @Override
    public PageResult<PluginMarketPublication> page(PluginPublicationStatus status, Long publisherUserId, int page, int size) {
        Query query = new Query();
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        if (publisherUserId != null) {
            query.addCriteria(Criteria.where("publisherUserId").is(publisherUserId));
        }
        query.with(Sort.by(Sort.Direction.DESC, "createTime"));
        long total = mongoTemplate.count(query, PluginMarketPublicationDO.class);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return new PageResult<>(
                mongoTemplate.find(query, PluginMarketPublicationDO.class).stream()
                        .map(PluginMarketPublicationInfraMapper::toDomain)
                        .toList(),
                total,
                currentPage,
                pageSize);
    }

    @Override
    public long incrementDownloadCount(Long id) {
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update().inc("downloadCount", 1L);
        PluginMarketPublicationDO updated = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), PluginMarketPublicationDO.class);
        return updated == null || updated.getDownloadCount() == null ? 0L : updated.getDownloadCount();
    }

    @Override
    public void deleteById(Long id) {
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(id)), PluginMarketPublicationDO.class);
    }
}
