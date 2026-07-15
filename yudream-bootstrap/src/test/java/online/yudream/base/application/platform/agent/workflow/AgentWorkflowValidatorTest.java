package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentWorkflowValidatorTest {

    private final AgentWorkflowValidator validator = new AgentWorkflowValidator(new ObjectMapper());

    @Test
    void rejectsWorkflowWithoutReachableEndNode() {
        assertThatThrownBy(() -> validator.validate(workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"end","data":{"kind":"end"}}
                """,
                """
                """
        ), AgentWorkflowValidator.Catalog.empty()))
                .isInstanceOf(AgentWorkflowDefinitionException.class)
                .hasMessageContaining("结束节点");
    }

    @Test
    void rejectsConditionWithoutBothBranches() {
        assertThatThrownBy(() -> validator.validate(workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"condition","data":{"kind":"condition","condition":"#input == 'yes'"}},
                {"id":"end","data":{"kind":"end"}}
                """,
                """
                {"source":"start","target":"condition"},
                {"source":"condition","target":"end","sourceHandle":"true"}
                """
        ), AgentWorkflowValidator.Catalog.empty()))
                .isInstanceOf(AgentWorkflowDefinitionException.class)
                .hasMessageContaining("true 和 false");
    }

    @Test
    void rejectsNodesWhoseRequiredConfigurationIsMissing() {
        assertThatThrownBy(() -> validator.validate(workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"llm","data":{"kind":"llm","providerCode":"","modelCode":""}},
                {"id":"end","data":{"kind":"end"}}
                """,
                """
                {"source":"start","target":"llm"},
                {"source":"llm","target":"end"}
                """
        ), AgentWorkflowValidator.Catalog.empty()))
                .isInstanceOf(AgentWorkflowDefinitionException.class)
                .hasMessageContaining("模型");
    }

    @Test
    void rejectsModelsKnowledgeSpacesAndToolsOutsidePublishedCatalog() {
        var catalog = new AgentWorkflowValidator.Catalog(
                Set.of(new AgentWorkflowValidator.ModelRef("openai", "gpt-5", "chat")),
                Set.of("manual"),
                Set.of("wiki.search")
        );
        assertThatThrownBy(() -> validator.validate(workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"search","data":{"kind":"search","knowledgeSpaceSlug":"missing"}},
                {"id":"end","data":{"kind":"end"}}
                """,
                """
                {"source":"start","target":"search"},
                {"source":"search","target":"end"}
                """
        ), catalog))
                .isInstanceOf(AgentWorkflowDefinitionException.class)
                .hasMessageContaining("知识空间");
    }

    @Test
    void acceptsFullyConfiguredWorkflowAndMigratesLegacyConditionLabels() {
        var catalog = new AgentWorkflowValidator.Catalog(
                Set.of(new AgentWorkflowValidator.ModelRef("openai", "gpt-5", "chat")),
                Set.of(),
                Set.of()
        );
        validator.validate(workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"condition","data":{"kind":"condition","condition":"#input != null"}},
                {"id":"yes","data":{"kind":"llm","providerCode":"openai","modelCode":"gpt-5"}},
                {"id":"no","data":{"kind":"template","template":"没有输入"}},
                {"id":"end","data":{"kind":"end"}}
                """,
                """
                {"source":"start","target":"condition"},
                {"source":"condition","target":"yes","sourceHandle":"source","label":"true"},
                {"source":"condition","target":"no","sourceHandle":"source","data":{"branch":"false"}},
                {"source":"yes","target":"end"},
                {"source":"no","target":"end"}
                """
        ), catalog);
    }

    private String workflow(String nodes, String edges) {
        return "{\"nodes\":[" + nodes + "],\"edges\":[" + edges + "]}";
    }
}
