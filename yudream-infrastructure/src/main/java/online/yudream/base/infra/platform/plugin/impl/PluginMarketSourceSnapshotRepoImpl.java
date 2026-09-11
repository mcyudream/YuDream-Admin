package online.yudream.base.infra.platform.plugin.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.plugin.repo.PluginMarketSourceSnapshotRepo;
import online.yudream.base.domain.platform.plugin.valobj.PluginMarketSourceSnapshot;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.plugin.dataobj.PluginMarketSourceSnapshotDO;
import online.yudream.base.infra.platform.plugin.mapper.PluginMarketSourceSnapshotInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PluginMarketSourceSnapshotRepoImpl implements PluginMarketSourceSnapshotRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public PluginMarketSourceSnapshot save(PluginMarketSourceSnapshot snapshot) {
        PluginMarketSourceSnapshotDO dataObj = PluginMarketSourceSnapshotInfraMapper.toDataObj(snapshot);
        PluginMarketSourceSnapshotDO existing = findByDataObj(snapshot.sourceId());
        if (existing != null) {
            dataObj.setId(existing.getId());
            dataObj.setVersion(existing.getVersion());
            dataObj.setCreateTime(existing.getCreateTime());
        } else {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        mongoTemplate.save(dataObj);
        return snapshot;
    }

    @Override
    public Optional<PluginMarketSourceSnapshot> findBySourceId(Long sourceId) {
        return Optional.ofNullable(PluginMarketSourceSnapshotInfraMapper.toDomain(findByDataObj(sourceId)));
    }

    @Override
    public void deleteBySourceId(Long sourceId) {
        mongoTemplate.remove(Query.query(Criteria.where("sourceId").is(sourceId)), PluginMarketSourceSnapshotDO.class);
    }

    private PluginMarketSourceSnapshotDO findByDataObj(Long sourceId) {
        return mongoTemplate.findOne(Query.query(Criteria.where("sourceId").is(sourceId)),
                PluginMarketSourceSnapshotDO.class);
    }
}
