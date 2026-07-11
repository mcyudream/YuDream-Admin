package online.yudream.base.infra.platform.satori.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;
import online.yudream.base.domain.platform.satori.repo.SatoriConnectionRepo;
import online.yudream.base.domain.platform.satori.service.SatoriCredentialCipher;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.satori.dataobj.SatoriConnectionDO;
import online.yudream.base.infra.platform.satori.mapper.SatoriInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SatoriConnectionRepoImpl implements SatoriConnectionRepo {
    private final MongoTemplate mongoTemplate; private final IdGenerator idGenerator; private final SatoriCredentialCipher cipher;
    @Override public SatoriConnection save(SatoriConnection connection) {
        SatoriConnectionDO data = SatoriInfraMapper.toDataObj(connection, cipher);
        if (data.getId() == null) { data.setId(idGenerator.nextId()); data.setCreateTime(LocalDateTime.now()); }
        data.setUpdateTime(LocalDateTime.now()); return SatoriInfraMapper.toDomain(mongoTemplate.save(data), cipher);
    }
    @Override public Optional<SatoriConnection> findById(Long id) { return Optional.ofNullable(SatoriInfraMapper.toDomain(mongoTemplate.findById(id, SatoriConnectionDO.class), cipher)); }
    @Override public List<SatoriConnection> findEnabled() { return mongoTemplate.find(Query.query(Criteria.where("enabled").is(true)), SatoriConnectionDO.class).stream().map(data -> SatoriInfraMapper.toDomain(data, cipher)).toList(); }
    @Override public PageResult<SatoriConnection> page(String keyword, int page, int size) {
        Query query = new Query(); if (StringUtils.hasText(keyword)) { String pattern = ".*" + Pattern.quote(keyword.trim()) + ".*"; query.addCriteria(new Criteria().orOperator(Criteria.where("name").regex(pattern, "i"), Criteria.where("baseUrl").regex(pattern, "i"))); }
        long total = mongoTemplate.count(query, SatoriConnectionDO.class); int current = Math.max(1, page); int limit = Math.max(1, size);
        query.with(Sort.by(Sort.Direction.DESC, "createTime")).skip((long) (current - 1) * limit).limit(limit);
        return new PageResult<>(mongoTemplate.find(query, SatoriConnectionDO.class).stream().map(data -> SatoriInfraMapper.toDomain(data, cipher)).toList(), total, current, limit);
    }
}
