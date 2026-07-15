package online.yudream.base.infra.platform.agent.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.agent.aggregate.AgentTool;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.agent.dataobj.AgentToolDO;
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
public class AgentToolRepoImpl implements AgentToolRepo {
    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;
    public AgentTool save(AgentTool value) { AgentToolDO data = AgentInfraMapper.toDataObj(value); if (data.getId() == null) { data.setId(idGenerator.nextId()); data.setCreateTime(LocalDateTime.now()); } data.setUpdateTime(LocalDateTime.now()); return AgentInfraMapper.toDomain(mongoTemplate.save(data)); }
    public Optional<AgentTool> findById(Long id) { return Optional.ofNullable(AgentInfraMapper.toDomain(mongoTemplate.findById(id, AgentToolDO.class))); }
    public Optional<AgentTool> findByCode(String code) { return Optional.ofNullable(AgentInfraMapper.toDomain(mongoTemplate.findOne(Query.query(Criteria.where("code").is(code)), AgentToolDO.class))); }
    public void deleteById(Long id) { mongoTemplate.remove(Query.query(Criteria.where("id").is(id)), AgentToolDO.class); }
    public PageResult<AgentTool> page(String keyword, int page, int size) { Query query = new Query(); if (StringUtils.hasText(keyword)) { String p = ".*" + Pattern.quote(keyword.trim()) + ".*"; query.addCriteria(new Criteria().orOperator(Criteria.where("name").regex(p, "i"), Criteria.where("code").regex(p, "i"), Criteria.where("description").regex(p, "i"))); } long total = mongoTemplate.count(query, AgentToolDO.class); int current = Math.max(1, page); int limit = Math.max(1, size); query.with(Sort.by(Sort.Direction.DESC, "updateTime")).skip((long) (current - 1) * limit).limit(limit); return new PageResult<>(mongoTemplate.find(query, AgentToolDO.class).stream().map(AgentInfraMapper::toDomain).toList(), total, current, limit); }
}
