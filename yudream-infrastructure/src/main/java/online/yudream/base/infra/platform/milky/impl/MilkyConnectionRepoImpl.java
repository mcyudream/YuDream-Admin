package online.yudream.base.infra.platform.milky.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.milky.dataobj.MilkyConnectionDO;
import online.yudream.base.infra.platform.milky.mapper.MilkyInfraMapper;
import online.yudream.base.infra.platform.milky.service.MilkyCredentialCipher;
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
public class MilkyConnectionRepoImpl implements MilkyConnectionRepo {
    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;
    private final MilkyCredentialCipher cipher;
    @Override public MilkyConnection save(MilkyConnection connection) {
        MilkyConnectionDO data = MilkyInfraMapper.toDataObj(connection, cipher);
        if (data.getId() == null) { data.setId(idGenerator.nextId()); data.setCreateTime(LocalDateTime.now()); }
        data.setUpdateTime(LocalDateTime.now());
        return MilkyInfraMapper.toDomain(mongoTemplate.save(data), cipher);
    }
    @Override public Optional<MilkyConnection> findById(Long id) { return Optional.ofNullable(MilkyInfraMapper.toDomain(mongoTemplate.findById(id, MilkyConnectionDO.class), cipher)); }
    @Override public List<MilkyConnection> findEnabled() { return mongoTemplate.find(Query.query(Criteria.where("enabled").is(true)), MilkyConnectionDO.class).stream().map(item -> MilkyInfraMapper.toDomain(item, cipher)).toList(); }
    @Override public PageResult<MilkyConnection> page(String keyword, int page, int size) {
        Query query = new Query(); int current = Math.max(1, page); int limit = Math.max(1, size); long total = mongoTemplate.count(query, MilkyConnectionDO.class);
        query.with(Sort.by(Sort.Direction.DESC, "createTime")).skip((long) (current - 1) * limit).limit(limit);
        return new PageResult<>(mongoTemplate.find(query, MilkyConnectionDO.class).stream().map(item -> MilkyInfraMapper.toDomain(item, cipher)).toList(), total, current, limit);
    }
}
