package online.yudream.base.infra.platform.satori.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.satori.aggregate.SatoriEventRecord;
import online.yudream.base.domain.platform.satori.repo.SatoriEventRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.satori.dataobj.SatoriEventRecordDO;
import online.yudream.base.infra.platform.satori.mapper.SatoriInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Optional;

@Service @RequiredArgsConstructor
public class SatoriEventRepoImpl implements SatoriEventRepo {
    private final MongoTemplate mongoTemplate; private final IdGenerator idGenerator;
    @Override public SatoriEventRecord save(SatoriEventRecord event) { SatoriEventRecordDO data = SatoriInfraMapper.toDataObj(event); if (data.getId() == null) { data.setId(idGenerator.nextId()); data.setCreateTime(LocalDateTime.now()); } data.setUpdateTime(LocalDateTime.now()); return SatoriInfraMapper.toDomain(mongoTemplate.save(data)); }
    @Override public Optional<SatoriEventRecord> findByIdempotencyKey(Long connectionId, String sequence) { Query query = Query.query(Criteria.where("connectionId").is(connectionId).and("sequence").is(sequence)); return Optional.ofNullable(SatoriInfraMapper.toDomain(mongoTemplate.findOne(query, SatoriEventRecordDO.class))); }
    @Override public PageResult<SatoriEventRecord> page(Long connectionId, int page, int size) { int current = Math.max(1, page); int limit = Math.max(1, size); Query query = Query.query(Criteria.where("connectionId").is(connectionId)); long total = mongoTemplate.count(query, SatoriEventRecordDO.class); query.with(Sort.by(Sort.Direction.DESC, "receivedAt")).skip((long) (current - 1) * limit).limit(limit); return new PageResult<>(mongoTemplate.find(query, SatoriEventRecordDO.class).stream().map(SatoriInfraMapper::toDomain).toList(), total, current, limit); }
}
