package online.yudream.base.infra.platform.satori.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.satori.aggregate.SatoriEventCursor;
import online.yudream.base.domain.platform.satori.repo.SatoriEventCursorRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.satori.dataobj.SatoriEventCursorDO;
import online.yudream.base.infra.platform.satori.mapper.SatoriInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SatoriEventCursorRepoImpl implements SatoriEventCursorRepo {
    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public Optional<SatoriEventCursor> findByConnectionId(Long connectionId) {
        return Optional.ofNullable(SatoriInfraMapper.toDomain(mongoTemplate.findOne(
                org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("connectionId").is(connectionId)),
                SatoriEventCursorDO.class)));
    }

    @Override
    public SatoriEventCursor save(SatoriEventCursor cursor) {
        SatoriEventCursorDO data = SatoriInfraMapper.toDataObj(cursor);
        if (data.getId() == null) {
            data.setId(idGenerator.nextId());
            data.setCreateTime(LocalDateTime.now());
        }
        data.setUpdateTime(LocalDateTime.now());
        return SatoriInfraMapper.toDomain(mongoTemplate.save(data));
    }
}
