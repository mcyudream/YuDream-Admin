package online.yudream.base.application.platform.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void derivesOrderedUniqueApplicationToolsFromModelAndLegacyToolNodes() {
        String workflow = workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"model","data":{"kind":"llm","toolCodes":["web.fetch","cms.patch","web.fetch"]}},
                {"id":"legacy","data":{"kind":"tool","toolCode":"cms.patch"}},
                {"id":"second","data":{"kind":"extract","toolCodes":["python.report"]}},
                {"id":"noise","data":{"kind":"template","template":"done","toolCodes":["client.smuggled"]}},
                {"id":"end","data":{"kind":"end"}}
                """,
                """
                {"source":"start","target":"model"},
                {"source":"model","target":"legacy"},
                {"source":"legacy","target":"second"},
                {"source":"second","target":"noise"},
                {"source":"noise","target":"end"}
                """
        );

        assertThat(AgentWorkflowToolCodes.derive(workflow))
                .containsExactly("web.fetch", "cms.patch", "python.report");
    }

    @Test
    void rejectsInvalidModelToolModes() {
        var catalog = chatCatalog(false, Set.of("web.fetch"));

        assertThatThrownBy(() -> validator.validate(workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"model","data":{"kind":"llm","providerCode":"openai","modelCode":"gpt-5","toolMode":"AUTO"}},
                {"id":"end","data":{"kind":"end"}}
                """,
                """
                {"source":"start","target":"model"},
                {"source":"model","target":"end"}
                """
        ), catalog)).isInstanceOf(AgentWorkflowDefinitionException.class);

        assertThatThrownBy(() -> validator.validate(workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"model","data":{"kind":"llm","providerCode":"openai","modelCode":"gpt-5","toolMode":"NONE","toolCodes":["web.fetch"]}},
                {"id":"end","data":{"kind":"end"}}
                """,
                """
                {"source":"start","target":"model"},
                {"source":"model","target":"end"}
                """
        ), catalog)).isInstanceOf(AgentWorkflowDefinitionException.class);
    }

    @Test
    void validatesExtractClassifyAndVisionSpecificSettings() {
        var nonVisionCatalog = chatCatalog(false, Set.of());
        assertThatThrownBy(() -> validator.validate(workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"extract","data":{"kind":"extract","providerCode":"openai","modelCode":"gpt-5","outputSchema":"[]"}},
                {"id":"end","data":{"kind":"end"}}
                """,
                """
                {"source":"start","target":"extract"},
                {"source":"extract","target":"end"}
                """
        ), nonVisionCatalog)).isInstanceOf(AgentWorkflowDefinitionException.class);

        assertThatThrownBy(() -> validator.validate(workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"classify","data":{"kind":"classify","providerCode":"openai","modelCode":"gpt-5","classes":["yes","yes"]}},
                {"id":"end","data":{"kind":"end"}}
                """,
                """
                {"source":"start","target":"classify"},
                {"source":"classify","target":"end"}
                """
        ), nonVisionCatalog)).isInstanceOf(AgentWorkflowDefinitionException.class);

        assertThatThrownBy(() -> validator.validate(workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"vision","data":{"kind":"vision","providerCode":"openai","modelCode":"gpt-5"}},
                {"id":"end","data":{"kind":"end"}}
                """,
                """
                {"source":"start","target":"vision"},
                {"source":"vision","target":"end"}
                """
        ), nonVisionCatalog)).isInstanceOf(AgentWorkflowDefinitionException.class);
    }

    @Test
    void acceptsLegacyUnderstandAndToolNodes() {
        var catalog = chatCatalog(false, Set.of("web.fetch"));
        validator.validate(workflow(
                """
                {"id":"start","data":{"kind":"start"}},
                {"id":"understand","data":{"kind":"understand","providerCode":"openai","modelCode":"gpt-5"}},
                {"id":"tool","data":{"kind":"tool","toolCode":"web.fetch"}},
                {"id":"end","data":{"kind":"end"}}
                """,
                """
                {"source":"start","target":"understand"},
                {"source":"understand","target":"tool"},
                {"source":"tool","target":"end"}
                """
        ), catalog);
    }

    private AgentWorkflowValidator.Catalog chatCatalog(boolean vision, Set<String> tools) {
        return new AgentWorkflowValidator.Catalog(
                Set.of(new AgentWorkflowValidator.ModelRef("openai", "gpt-5", "chat", vision)),
                Set.of(),
                tools
        );
    }

    private String workflow(String nodes, String edges) {
        return "{\"nodes\":[" + nodes + "],\"edges\":[" + edges + "]}";
    }
}
