package online.yudream.base.infra.platform.satori.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.satori.aggregate.SatoriOperationLog;
import online.yudream.base.domain.platform.satori.repo.SatoriOperationLogRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.satori.dataobj.SatoriOperationLogDO;
import online.yudream.base.infra.platform.satori.mapper.SatoriInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SatoriOperationLogRepoImpl implements SatoriOperationLogRepo {
    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public SatoriOperationLog save(SatoriOperationLog log) {
        SatoriOperationLogDO data = SatoriInfraMapper.toDataObj(log);
        data.setId(idGenerator.nextId());
        data.setCreateTime(LocalDateTime.now());
        data.setUpdateTime(data.getCreateTime());
        return SatoriInfraMapper.toDomain(mongoTemplate.insert(data));
    }

    @Override
    public PageResult<SatoriOperationLog> page(Long connectionId, int page, int size) {
        int current = Math.max(page, 1);
        int limit = Math.max(size, 1);
        Query query = Query.query(Criteria.where("connectionId").is(connectionId));
        long total = mongoTemplate.count(query, SatoriOperationLogDO.class);
        query.with(Sort.by(Sort.Direction.DESC, "occurredAt")).skip((long) (current - 1) * limit).limit(limit);
        return new PageResult<>(mongoTemplate.find(query, SatoriOperationLogDO.class).stream()
                .map(SatoriInfraMapper::toDomain).toList(), total, current, limit);
    }
}
