package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.skill.AgentSkill;
import online.yudream.base.infra.platform.agent.dataobj.AgentSkillDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoAgentSkillRepositoryTest extends MongoStoreTestBase {

    private MongoAgentSkillRepository repository;

    @BeforeEach
    void initRepository() {
        repository = new MongoAgentSkillRepository(mongo);
    }

    @Test
    void saveThenGetSkillRoundTripsContentAndResources() {
        repository.save(List.of(AgentSkill.builder()
                .name("code-reviewer")
                .description("代码评审技能")
                .skillContent("# Code Reviewer\n按规范评审。")
                .resources(Map.of("references/style.md", "风格指南内容"))
                .build()), true);

        AgentSkill skill = repository.getSkill("code-reviewer");
        assertNotNull(skill);
        assertEquals("代码评审技能", skill.getDescription());
        assertTrue(skill.getSkillContent().contains("Code Reviewer"));
        assertEquals("风格指南内容", skill.getResource("references/style.md"));
        assertTrue(repository.skillExists("code-reviewer"));
    }

    @Test
    void disabledSkillsAreInvisible() {
        repository.save(List.of(AgentSkill.builder()
                .name("draft-skill")
                .description("草稿技能")
                .skillContent("内容")
                .build()), true);
        // 直接置为停用（管理端 setEnabled 路径）
        for (AgentSkillDO doc : mongo.find(new Query(), AgentSkillDO.class)) {
            doc.setEnabled(false);
            mongo.save(doc);
        }

        assertNull(repository.getSkill("draft-skill"));
        assertTrue(repository.getAllSkillNames().isEmpty());
        assertTrue(repository.getAllSkills().isEmpty());
    }

    @Test
    void saveWithoutOverwriteKeepsExisting() {
        repository.save(List.of(AgentSkill.builder()
                .name("s1")
                .description("旧描述")
                .skillContent("旧内容")
                .build()), true);
        repository.save(List.of(AgentSkill.builder()
                .name("s1")
                .description("新描述")
                .skillContent("新内容")
                .build()), false);

        assertEquals("旧描述", repository.getSkill("s1").getDescription());
        repository.save(List.of(AgentSkill.builder()
                .name("s1")
                .description("新描述")
                .skillContent("新内容")
                .build()), true);
        assertEquals("新描述", repository.getSkill("s1").getDescription());
    }

    @Test
    void deleteRemovesSkill() {
        repository.save(List.of(AgentSkill.builder()
                .name("gone")
                .description("待删除")
                .skillContent("x")
                .build()), true);
        assertTrue(repository.delete("gone"));
        assertFalse(repository.skillExists("gone"));
    }

    @Test
    void writeableFlagGatesMutations() {
        repository.setWriteable(false);
        assertFalse(repository.save(List.of(AgentSkill.builder()
                .name("s2")
                .description("d")
                .skillContent("c")
                .build()), true));
        assertFalse(repository.delete("s2"));
        assertFalse(repository.getRepositoryInfo().isWritable());
    }
}
