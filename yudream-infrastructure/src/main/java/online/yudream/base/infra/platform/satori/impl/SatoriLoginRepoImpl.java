package online.yudream.base.infra.platform.satori.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.satori.aggregate.SatoriLogin;
import online.yudream.base.domain.platform.satori.repo.SatoriLoginRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.satori.dataobj.SatoriLoginDO;
import online.yudream.base.infra.platform.satori.mapper.SatoriInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service @RequiredArgsConstructor
public class SatoriLoginRepoImpl implements SatoriLoginRepo {
    private final MongoTemplate mongoTemplate; private final IdGenerator idGenerator;
    @Override public SatoriLogin save(SatoriLogin login) { SatoriLoginDO data = SatoriInfraMapper.toDataObj(login); if (data.getId() == null) { data.setId(idGenerator.nextId()); data.setCreateTime(LocalDateTime.now()); } data.setUpdateTime(LocalDateTime.now()); return SatoriInfraMapper.toDomain(mongoTemplate.save(data)); }
    @Override public Optional<SatoriLogin> findByNaturalKey(Long connectionId, String platform, String userId) { Query query = Query.query(Criteria.where("connectionId").is(connectionId).and("platform").is(platform).and("userId").is(userId)); return Optional.ofNullable(SatoriInfraMapper.toDomain(mongoTemplate.findOne(query, SatoriLoginDO.class))); }
    @Override public List<SatoriLogin> findByConnectionId(Long connectionId) { return mongoTemplate.find(Query.query(Criteria.where("connectionId").is(connectionId)), SatoriLoginDO.class).stream().map(SatoriInfraMapper::toDomain).toList(); }
}
