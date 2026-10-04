package online.yudream.base.infra.platform.agent.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.agent.aggregate.AgentSkill;
import online.yudream.base.domain.platform.agent.repo.AgentSkillRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.agent.dataobj.AgentSkillDO;
import online.yudream.base.infra.platform.agent.mapper.AgentInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AgentSkillRepoImpl implements AgentSkillRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public AgentSkill save(AgentSkill value) {
        AgentSkillDO data = AgentInfraMapper.toDataObj(value);
        if (data.getId() == null) {
            data.setId(idGenerator.nextId());
            data.setCreateTime(LocalDateTime.now());
        }
        data.setUpdateTime(LocalDateTime.now());
        return AgentInfraMapper.toDomain(mongoTemplate.save(data));
    }

    @Override
    public Optional<AgentSkill> findById(Long id) {
        return Optional.ofNullable(AgentInfraMapper.toDomain(mongoTemplate.findById(id, AgentSkillDO.class)));
    }

    @Override
    public Optional<AgentSkill> findByName(String name) {
        return Optional.ofNullable(AgentInfraMapper.toDomain(
                mongoTemplate.findOne(Query.query(Criteria.where("name").is(name)), AgentSkillDO.class)));
    }

    @Override
    public PageResult<AgentSkill> page(String keyword, int page, int size) {
        Query query = new Query();
        if (StringUtils.hasText(keyword)) {
            String pattern = ".*" + Pattern.quote(keyword.trim()) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("name").regex(pattern, "i"),
                    Criteria.where("description").regex(pattern, "i")));
        }
        long total = mongoTemplate.count(query, AgentSkillDO.class);
        int current = Math.max(1, page);
        int limit = Math.max(1, size);
        query.with(Sort.by(Sort.Direction.DESC, "updateTime")).skip((long) (current - 1) * limit).limit(limit);
        return new PageResult<>(mongoTemplate.find(query, AgentSkillDO.class).stream()
                .map(AgentInfraMapper::toDomain).toList(), total, current, limit);
    }

    @Override
    public List<AgentSkill> findAllEnabled() {
        return mongoTemplate.find(Query.query(Criteria.where("enabled").is(true))
                        .with(Sort.by(Sort.Direction.ASC, "name")), AgentSkillDO.class)
                .stream().map(AgentInfraMapper::toDomain).toList();
    }

    @Override
    public void deleteById(Long id) {
        mongoTemplate.remove(Query.query(Criteria.where("id").is(id)), AgentSkillDO.class);
    }
}
