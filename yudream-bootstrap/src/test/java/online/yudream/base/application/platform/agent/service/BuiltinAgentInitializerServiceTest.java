package online.yudream.base.application.platform.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.agent.assembler.AgentModelCatalogParser;
import online.yudream.base.application.platform.agent.dto.AgentModelDTO;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;
import online.yudream.base.domain.platform.agent.repo.AgentApplicationRepo;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuiltinAgentInitializerServiceTest {

    @Test
    void createsPublishedComplexCmsAndAguiCardAgentsFromConfiguredDefaultModel() throws Exception {
        AgentApplicationRepo applications = mock(AgentApplicationRepo.class);
        CapabilityModuleRepo capabilities = mock(CapabilityModuleRepo.class);
        AgentModelCatalogParser models = mock(AgentModelCatalogParser.class);
        CapabilityModule ai = mock(CapabilityModule.class);
        when(ai.getConfig()).thenReturn(Map.of("providers", "[]"));
        when(capabilities.findByCode("ai")).thenReturn(Optional.of(ai));
        when(models.parse(any())).thenReturn(List.of(new AgentModelDTO(
                "openai", "OpenAI", "gpt-5", "GPT-5", "chat", true, true, true
        )));
        when(applications.findByCode(any())).thenReturn(Optional.empty());
        when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        BuiltinAgentInitializerService service = new BuiltinAgentInitializerService(applications, capabilities, models, new ObjectMapper());

        List<AgentApplication> created = service.initialize();

        assertThat(created).extracting(AgentApplication::getCode)
                .containsExactlyInAnyOrder(
                        BuiltinAgentCodes.CMS_BUILDER,
                        BuiltinAgentCodes.AGUI_CARD
                );
        assertThat(created).allMatch(item -> item.getStatus() == AgentApplicationStatus.PUBLISHED);
        assertThat(created).allMatch(item -> item.getWorkflowJson().contains("\"providerCode\":\"openai\""));
        AgentApplication cmsAgent = created.stream()
                .filter(item -> BuiltinAgentCodes.CMS_BUILDER.equals(item.getCode()))
                .findFirst()
                .orElseThrow();
        assertThat(cmsAgent.getToolCodes())
                .contains("cms.canvas.patch", "cms.canvas.validate");
        var cmsNodes = new ObjectMapper().readTree(cmsAgent.getWorkflowJson()).path("nodes");
        assertThat(cmsNodes).hasSize(8);
        assertThat(cmsNodes.toString()).contains("\"kind\":\"understand\"", "\"kind\":\"condition\"", "\"kind\":\"template\"");
        assertThat(textValues(nodeData(cmsNodes, "build").path("toolCodes")))
                .containsExactly(
                        "web.fetch", "cms.ask.user", "cms.canvas.patch", "cms.chrome.style",
                        "cms.canvas.selected.text", "cms.canvas.selected.html", "cms.canvas.selected.style",
                        "cms.canvas.block.add", "cms.canvas.selected.remove", "cms.block.template.list",
                        "cms.canvas.validate"
                );
        assertThat(nodeData(cmsNodes, "build").path("toolMode").asText()).isEqualTo("AUTO");
        assertThat(textValues(nodeData(cmsNodes, "plan").path("toolCodes"))).isEmpty();
        assertThat(nodeData(cmsNodes, "plan").path("toolMode").asText()).isBlank();
        assertThat(textValues(nodeData(cmsNodes, "clarify").path("toolCodes"))).isEmpty();
        assertThat(nodeData(cmsNodes, "clarify").path("toolMode").asText()).isBlank();
        AgentApplication cardAgent = created.stream()
                .filter(item -> BuiltinAgentCodes.AGUI_CARD.equals(item.getCode()))
                .findFirst()
                .orElseThrow();
        var cardNodes = new ObjectMapper().readTree(cardAgent.getWorkflowJson()).path("nodes");
        assertThat(cardNodes).hasSize(8);
        assertThat(cardNodes.toString()).contains("\"kind\":\"condition\"", "\"kind\":\"template\"");
        assertThat(cardAgent.getSystemPrompt()).contains("fields", "actions");
        verify(applications, never()).findByCode(BuiltinAgentCodes.LEGACY_GROUP_CHATBOT);
    }

    @Test
    void keepsExistingBuiltinAgentsUntouched() {
        AgentApplicationRepo applications = mock(AgentApplicationRepo.class);
        CapabilityModuleRepo capabilities = mock(CapabilityModuleRepo.class);
        AgentModelCatalogParser models = mock(AgentModelCatalogParser.class);
        AgentApplication existing = AgentApplication.builder()
                .code(BuiltinAgentCodes.CMS_BUILDER)
                .status(AgentApplicationStatus.PUBLISHED)
                .workflowJson("{\"nodes\":[{},{},{},{}]}")
                .build();
        when(applications.findByCode(BuiltinAgentCodes.CMS_BUILDER)).thenReturn(Optional.of(existing));
        when(applications.findByCode(BuiltinAgentCodes.AGUI_CARD)).thenReturn(Optional.of(existing));
        BuiltinAgentInitializerService service = new BuiltinAgentInitializerService(applications, capabilities, models, new ObjectMapper());

        assertThat(service.initialize()).isEmpty();
        verify(applications, never()).save(any());
    }

    @Test
    void upgradesExistingThreeNodeBuiltinWorkflowWithoutTouchingPluginAgent() throws Exception {
        AgentApplicationRepo applications = mock(AgentApplicationRepo.class);
        CapabilityModuleRepo capabilities = mock(CapabilityModuleRepo.class);
        AgentModelCatalogParser models = mock(AgentModelCatalogParser.class);
        CapabilityModule ai = mock(CapabilityModule.class);
        when(ai.getConfig()).thenReturn(Map.of("providers", "[]"));
        when(capabilities.findByCode("ai")).thenReturn(Optional.of(ai));
        when(models.parse(any())).thenReturn(List.of(new AgentModelDTO(
                "openai", "OpenAI", "gpt-5", "GPT-5", "chat", true, true, true
        )));
        AgentApplication cms = AgentApplication.builder()
                .code(BuiltinAgentCodes.CMS_BUILDER)
                .name("CMS 页面构建 Agent")
                .status(AgentApplicationStatus.PUBLISHED)
                .workflowJson("{\"nodes\":[{},{},{}]}")
                .build();
        AgentApplication card = AgentApplication.builder()
                .code(BuiltinAgentCodes.AGUI_CARD)
                .name("AG-UI 卡片生成 Agent")
                .status(AgentApplicationStatus.PUBLISHED)
                .workflowJson("{\"nodes\":[{},{},{}]}")
                .build();
        when(applications.findByCode(BuiltinAgentCodes.CMS_BUILDER)).thenReturn(Optional.of(cms));
        when(applications.findByCode(BuiltinAgentCodes.AGUI_CARD)).thenReturn(Optional.of(card));
        when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<AgentApplication> upgraded = new BuiltinAgentInitializerService(
                applications, capabilities, models, new ObjectMapper()
        ).initialize();

        assertThat(upgraded).hasSize(2);
        assertThat(new ObjectMapper().readTree(cms.getWorkflowJson()).path("nodes")).hasSize(8);
        assertThat(new ObjectMapper().readTree(card.getWorkflowJson()).path("nodes")).hasSize(8);
        verify(applications, never()).findByCode(BuiltinAgentCodes.LEGACY_GROUP_CHATBOT);
    }

    @Test
    void upgradesCmsWorkflowMissingNodeToolConfigurationWithoutReenablingDisabledApplication() throws Exception {
        AgentApplicationRepo applications = mock(AgentApplicationRepo.class);
        CapabilityModuleRepo capabilities = mock(CapabilityModuleRepo.class);
        AgentModelCatalogParser models = mock(AgentModelCatalogParser.class);
        CapabilityModule ai = mock(CapabilityModule.class);
        when(ai.getConfig()).thenReturn(Map.of("providers", "[]"));
        when(capabilities.findByCode("ai")).thenReturn(Optional.of(ai));
        when(models.parse(any())).thenReturn(List.of(new AgentModelDTO(
                "openai", "OpenAI", "gpt-5", "GPT-5", "chat", true, true, true
        )));
        AgentApplication disabledCms = AgentApplication.builder()
                .code(BuiltinAgentCodes.CMS_BUILDER)
                .name("CMS 页面构建 Agent")
                .status(AgentApplicationStatus.DISABLED)
                .toolCodes(List.of("cms.canvas.patch"))
                .workflowJson("""
                        {"nodes":[
                          {"id":"start","data":{"kind":"start"}},
                          {"id":"plan","data":{"kind":"understand","strictJson":false,"providerCode":"openai","modelCode":"gpt-5"}},
                          {"id":"route","data":{"kind":"condition"}},
                          {"id":"clarify-task","data":{"kind":"template"}},
                          {"id":"build-task","data":{"kind":"template"}},
                          {"id":"clarify","data":{"kind":"llm","providerCode":"openai","modelCode":"gpt-5"}},
                          {"id":"build","data":{"kind":"llm","providerCode":"openai","modelCode":"gpt-5"}},
                          {"id":"end","data":{"kind":"end"}}
                        ]}
                        """)
                .build();
        when(applications.findByCode(BuiltinAgentCodes.CMS_BUILDER)).thenReturn(Optional.of(disabledCms));
        when(applications.findByCode(BuiltinAgentCodes.AGUI_CARD)).thenReturn(Optional.empty());
        when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<AgentApplication> upgraded = new BuiltinAgentInitializerService(
                applications, capabilities, models, new ObjectMapper()
        ).initialize();

        AgentApplication cms = upgraded.stream()
                .filter(item -> BuiltinAgentCodes.CMS_BUILDER.equals(item.getCode()))
                .findFirst()
                .orElseThrow();
        JsonNode nodes = new ObjectMapper().readTree(cms.getWorkflowJson()).path("nodes");
        assertThat(cms.getStatus()).isEqualTo(AgentApplicationStatus.DISABLED);
        assertThat(textValues(nodeData(nodes, "build").path("toolCodes")))
                .contains("cms.canvas.patch", "cms.canvas.validate");
        assertThat(nodeData(nodes, "build").path("toolMode").asText()).isEqualTo("AUTO");
        assertThat(textValues(nodeData(nodes, "plan").path("toolCodes"))).isEmpty();
        assertThat(textValues(nodeData(nodes, "clarify").path("toolCodes"))).isEmpty();
    }

    @Test
    void repairsComplexBuiltinWorkflowThatReferencesNonChatModel() throws Exception {
        AgentApplicationRepo applications = mock(AgentApplicationRepo.class);
        CapabilityModuleRepo capabilities = mock(CapabilityModuleRepo.class);
        AgentModelCatalogParser models = mock(AgentModelCatalogParser.class);
        CapabilityModule ai = mock(CapabilityModule.class);
        when(ai.getConfig()).thenReturn(Map.of("providers", "[]"));
        when(capabilities.findByCode("ai")).thenReturn(Optional.of(ai));
        when(models.parse(any())).thenReturn(List.of(
                new AgentModelDTO("openai", "OpenAI", "gpt-5", "GPT-5", "chat", true, true, true),
                new AgentModelDTO("openai", "OpenAI", "BAAI/bge-m3", "BGE M3", "embedding", false, true, false)
        ));
        AgentApplication cms = AgentApplication.builder()
                .code(BuiltinAgentCodes.CMS_BUILDER)
                .name("CMS 页面构建 Agent")
                .status(AgentApplicationStatus.PUBLISHED)
                .workflowJson("""
                        {"nodes":[
                          {"id":"start","data":{"kind":"start"}},
                          {"id":"plan","data":{"kind":"understand","providerCode":"openai","modelCode":"BAAI/bge-m3"}},
                          {"id":"route","data":{"kind":"condition"}},
                          {"id":"task","data":{"kind":"template"}},
                          {"id":"build","data":{"kind":"llm","providerCode":"openai","modelCode":"BAAI/bge-m3"}},
                          {"id":"end","data":{"kind":"end"}}
                        ]}
                        """)
                .build();
        when(applications.findByCode(BuiltinAgentCodes.CMS_BUILDER)).thenReturn(Optional.of(cms));
        when(applications.findByCode(BuiltinAgentCodes.AGUI_CARD)).thenReturn(Optional.empty());
        when(applications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<AgentApplication> repaired = new BuiltinAgentInitializerService(
                applications, capabilities, models, new ObjectMapper()
        ).initialize();

        assertThat(repaired).extracting(AgentApplication::getCode)
                .contains(BuiltinAgentCodes.CMS_BUILDER);
        assertThat(cms.getWorkflowJson())
                .contains("\"modelCode\":\"gpt-5\"")
                .doesNotContain("BAAI/bge-m3");
    }

    private JsonNode nodeData(JsonNode nodes, String id) {
        for (JsonNode node : nodes) {
            if (id.equals(node.path("id").asText())) {
                return node.path("data");
            }
        }
        throw new AssertionError("Missing workflow node: " + id);
    }

    private List<String> textValues(JsonNode values) {
        if (!values.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .toList();
    }
}
