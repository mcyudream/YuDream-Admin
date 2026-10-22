package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.infra.platform.agent.dataobj.AgentSkillDO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * AgentScope 技能仓库的 MongoDB 实现：平台「技能库」作为框架级统一技能源，
 * 所有 Harness Agent 经 skillRepository 挂载；启用的技能随每轮推理进入
 * &lt;available_skills&gt; 目录，由 load_skill_through_path 按需加载。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class MongoAgentSkillRepository implements AgentSkillRepository {

    public static final String SOURCE = "mongo";

    private final MongoTemplate mongo;
    private boolean writeable = true;

    public MongoAgentSkillRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public AgentSkill getSkill(String name) {
        return findEnabled(name).map(this::toFramework).orElse(null);
    }

    @Override
    public List<String> getAllSkillNames() {
        return allEnabled().stream().map(AgentSkillDO::getName).toList();
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        return allEnabled().stream().map(this::toFramework).toList();
    }

    @Override
    public boolean save(List<AgentSkill> skills, boolean overwrite) {
        if (!writeable || skills == null) {
            return false;
        }
        for (AgentSkill skill : skills) {
            Optional<AgentSkillDO> existing = Optional.ofNullable(mongo.findOne(
                    Query.query(Criteria.where("name").is(skill.getName())), AgentSkillDO.class));
            if (existing.isPresent() && !overwrite) {
                continue;
            }
            AgentSkillDO data = existing.orElseGet(AgentSkillDO::new);
            data.setName(skill.getName());
            data.setDescription(skill.getDescription());
            data.setContent(skill.getSkillContent());
            data.setResourcesJson(ToolSchemas.resultJson(skill.getResources()));
            data.setEnabled(true);
            data.setBuiltin(false);
            if (data.getId() == null) {
                data.setCreateTime(LocalDateTime.now());
            }
            data.setUpdateTime(LocalDateTime.now());
            mongo.save(data);
        }
        return true;
    }

    @Override
    public boolean delete(String name) {
        if (!writeable) {
            return false;
        }
        mongo.remove(Query.query(Criteria.where("name").is(name)), AgentSkillDO.class);
        return true;
    }

    @Override
    public boolean skillExists(String name) {
        return StringUtils.hasText(name)
                && mongo.count(Query.query(Criteria.where("name").is(name)), AgentSkillDO.class) > 0;
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return new AgentSkillRepositoryInfo("mongodb", "platformAgentSkill", writeable);
    }

    @Override
    public String getSource() {
        return SOURCE;
    }

    @Override
    public void setWriteable(boolean writeable) {
        this.writeable = writeable;
    }

    @Override
    public boolean isWriteable() {
        return writeable;
    }

    /**
     * 只读视图：只暴露名单内的已启用技能（对话中由用户选择的技能）；Agent 装配时创建，
     * 跨线程使用安全（不依赖线程局部状态）。
     */
    public AgentSkillRepository selected(Set<String> names) {
        Set<String> allowed = names == null ? Set.of() : Set.copyOf(names);
        MongoAgentSkillRepository source = this;
        return new AgentSkillRepository() {
            @Override
            public AgentSkill getSkill(String name) {
                return allowed.contains(name) ? source.getSkill(name) : null;
            }

            @Override
            public List<String> getAllSkillNames() {
                return source.getAllSkillNames().stream().filter(allowed::contains).toList();
            }

            @Override
            public List<AgentSkill> getAllSkills() {
                return source.allEnabled().stream().filter(data -> allowed.contains(data.getName())).map(source::toFramework).toList();
            }

            @Override
            public boolean save(List<AgentSkill> skills, boolean overwrite) {
                return false;
            }

            @Override
            public boolean delete(String name) {
                return false;
            }

            @Override
            public boolean skillExists(String name) {
                return allowed.contains(name) && source.skillExists(name);
            }

            @Override
            public AgentSkillRepositoryInfo getRepositoryInfo() {
                return new AgentSkillRepositoryInfo("mongodb", "platformAgentSkill（本次对话选择）", false);
            }

            @Override
            public String getSource() {
                return SOURCE;
            }

            @Override
            public void setWriteable(boolean writeable) {
            }

            @Override
            public boolean isWriteable() {
                return false;
            }
        };
    }

    private List<AgentSkillDO> allEnabled() {
        return mongo.find(Query.query(Criteria.where("enabled").is(true)), AgentSkillDO.class);
    }

    private Optional<AgentSkillDO> findEnabled(String name) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(Criteria.where("name").is(name).and("enabled").is(true)), AgentSkillDO.class));
    }

    private AgentSkill toFramework(AgentSkillDO data) {
        Map<String, String> resources = parseResources(data.getResourcesJson());
        return AgentSkill.builder()
                .name(data.getName())
                .description(data.getDescription())
                .skillContent(data.getContent())
                .resources(resources)
                .source(SOURCE)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parseResources(String resourcesJson) {
        if (!StringUtils.hasText(resourcesJson)) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(resourcesJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
