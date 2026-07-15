package online.yudream.base.infra.platform.agent.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;
import online.yudream.base.domain.platform.agent.repo.AgentApplicationRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.agent.dataobj.AgentApplicationDO;
import online.yudream.base.infra.platform.agent.mapper.AgentInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AgentApplicationRepoImpl implements AgentApplicationRepo {
    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    public AgentApplication save(AgentApplication value) {
        AgentApplicationDO data = AgentInfraMapper.toDataObj(value);
        if (data.getId() == null) { data.setId(idGenerator.nextId()); data.setCreateTime(LocalDateTime.now()); }
        data.setUpdateTime(LocalDateTime.now());
        return AgentInfraMapper.toDomain(mongoTemplate.save(data));
    }
    public Optional<AgentApplication> findById(Long id) { return Optional.ofNullable(AgentInfraMapper.toDomain(mongoTemplate.findById(id, AgentApplicationDO.class))); }
    public Optional<AgentApplication> findByCode(String code) { return Optional.ofNullable(AgentInfraMapper.toDomain(mongoTemplate.findOne(Query.query(Criteria.where("code").is(code)), AgentApplicationDO.class))); }
    public java.util.List<AgentApplication> findByToolCode(String toolCode) {
        return mongoTemplate.find(Query.query(Criteria.where("toolCodes").is(toolCode)), AgentApplicationDO.class).stream()
                .map(AgentInfraMapper::toDomain)
                .toList();
    }
    public void deleteById(Long id) { mongoTemplate.remove(Query.query(Criteria.where("id").is(id)), AgentApplicationDO.class); }
    public PageResult<AgentApplication> page(String keyword, AgentApplicationStatus status, int page, int size) {
        Query query = new Query();
        if (status != null) query.addCriteria(Criteria.where("status").is(status));
        if (StringUtils.hasText(keyword)) { String p = ".*" + Pattern.quote(keyword.trim()) + ".*"; query.addCriteria(new Criteria().orOperator(Criteria.where("name").regex(p, "i"), Criteria.where("code").regex(p, "i"), Criteria.where("description").regex(p, "i"))); }
        long total = mongoTemplate.count(query, AgentApplicationDO.class); int current = Math.max(1, page); int limit = Math.max(1, size);
        query.with(Sort.by(Sort.Direction.DESC, "updateTime")).skip((long) (current - 1) * limit).limit(limit);
        return new PageResult<>(mongoTemplate.find(query, AgentApplicationDO.class).stream().map(AgentInfraMapper::toDomain).toList(), total, current, limit);
    }
}
