package online.yudream.base.application.platform.agent.service;

import online.yudream.base.application.platform.agent.assembler.AgentModelCatalogParser;
import online.yudream.base.application.platform.agent.dto.AgentModelDTO;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;
import online.yudream.base.domain.platform.agent.repo.AgentApplicationRepo;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    void createsPublishedCmsAndChatbotAgentsFromConfiguredDefaultModel() {
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
                .containsExactlyInAnyOrder(BuiltinAgentCodes.CMS_BUILDER, BuiltinAgentCodes.GROUP_CHATBOT);
        assertThat(created).allMatch(item -> item.getStatus() == AgentApplicationStatus.PUBLISHED);
        assertThat(created).allMatch(item -> item.getWorkflowJson().contains("\"providerCode\":\"openai\""));
        assertThat(created.stream().filter(item -> BuiltinAgentCodes.CMS_BUILDER.equals(item.getCode())).findFirst().orElseThrow().getToolCodes())
                .contains("cms.canvas.patch", "cms.canvas.validate");
    }

    @Test
    void keepsExistingBuiltinAgentsUntouched() {
        AgentApplicationRepo applications = mock(AgentApplicationRepo.class);
        CapabilityModuleRepo capabilities = mock(CapabilityModuleRepo.class);
        AgentModelCatalogParser models = mock(AgentModelCatalogParser.class);
        AgentApplication existing = AgentApplication.builder().code(BuiltinAgentCodes.CMS_BUILDER).build();
        when(applications.findByCode(BuiltinAgentCodes.CMS_BUILDER)).thenReturn(Optional.of(existing));
        when(applications.findByCode(BuiltinAgentCodes.GROUP_CHATBOT)).thenReturn(Optional.of(existing));
        BuiltinAgentInitializerService service = new BuiltinAgentInitializerService(applications, capabilities, models, new ObjectMapper());

        assertThat(service.initialize()).isEmpty();
        verify(applications, never()).save(any());
    }
}
