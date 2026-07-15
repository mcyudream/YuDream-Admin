package online.yudream.base.application.platform.agent.service;

import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.assembler.AgentModelCatalogParser;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowValidator;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowRuntimeResult;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;
import online.yudream.base.domain.platform.agent.repo.AgentApplicationRepo;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.agent.service.AgentRuntimeApplicationRegistry;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AgentApplicationConsumerTest {

    @Test
    void listsPublishedApplicationsAndRunsByStableCode() {
        AgentApplicationRepo applicationRepo = mock(AgentApplicationRepo.class);
        AgentWorkflowRuntimeService workflowRuntime = mock(AgentWorkflowRuntimeService.class);
        AgentRuntimeApplicationRegistry runtimeApplications = mock(AgentRuntimeApplicationRegistry.class);
        when(runtimeApplications.applications()).thenReturn(List.of());
        when(runtimeApplications.findByCode(any())).thenReturn(java.util.Optional.empty());
        @SuppressWarnings("unchecked") ObjectProvider<AiAgentTool> tools = mock(ObjectProvider.class);
        when(tools.stream()).thenReturn(Stream.empty());
        AgentAppService service = new AgentAppService(
                mock(CapabilityAppService.class), mock(CapabilityModuleRepo.class), applicationRepo,
                mock(AgentToolRepo.class), tools, mock(AgentModelCatalogParser.class), mock(WikiSpaceRepo.class),
                workflowRuntime, mock(AgentWorkflowValidator.class), permission -> true, runtimeApplications
        );
        AgentApplication application = AgentApplication.builder()
                .id(7L).code("consumer-agent").name("消费 Agent")
                .status(AgentApplicationStatus.PUBLISHED).toolCodes(List.of()).workflowJson("{}")
                .build();
        when(applicationRepo.page(null, AgentApplicationStatus.PUBLISHED, 1, 200))
                .thenReturn(new PageResult<>(List.of(application), 1, 1, 200));
        when(applicationRepo.findByCode("consumer-agent")).thenReturn(java.util.Optional.of(application));
        when(workflowRuntime.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AgentWorkflowRuntimeResult("done", List.of()));
        AgentRunCmd command = new AgentRunCmd();
        command.setInput("hello");

        assertThat(service.publishedApplications()).extracting(item -> item.getCode())
                .containsExactly("consumer-agent");
        assertThat(service.runByCode("consumer-agent", command).getContent()).isEqualTo("done");
        verify(workflowRuntime).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void exposesPluginAgentOnlyWhileRuntimeContributionIsRegistered() {
        AgentApplicationRepo applicationRepo = mock(AgentApplicationRepo.class);
        AgentWorkflowRuntimeService workflowRuntime = mock(AgentWorkflowRuntimeService.class);
        AgentRuntimeApplicationRegistry runtimeApplications = mock(AgentRuntimeApplicationRegistry.class);
        @SuppressWarnings("unchecked") ObjectProvider<AiAgentTool> tools = mock(ObjectProvider.class);
        when(tools.stream()).thenReturn(Stream.empty());
        AgentApplication persisted = AgentApplication.builder()
                .id(7L).code("consumer-agent").name("消费 Agent")
                .status(AgentApplicationStatus.PUBLISHED).toolCodes(List.of()).workflowJson("{}")
                .build();
        AgentApplication legacy = AgentApplication.builder()
                .id(8L).code(BuiltinAgentCodes.LEGACY_GROUP_CHATBOT).name("旧机器人")
                .status(AgentApplicationStatus.PUBLISHED).toolCodes(List.of()).workflowJson("{}")
                .build();
        AgentApplication runtime = AgentApplication.builder()
                .id(-1L).code(BuiltinAgentCodes.LEGACY_GROUP_CHATBOT).name("插件机器人")
                .status(AgentApplicationStatus.PUBLISHED).toolCodes(List.of()).workflowJson("{}")
                .build();
        when(applicationRepo.page(null, AgentApplicationStatus.PUBLISHED, 1, 200))
                .thenReturn(new PageResult<>(List.of(persisted, legacy), 2, 1, 200));
        when(runtimeApplications.applications()).thenReturn(List.of(runtime));
        when(runtimeApplications.findByCode(BuiltinAgentCodes.LEGACY_GROUP_CHATBOT))
                .thenReturn(java.util.Optional.of(runtime));
        when(workflowRuntime.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AgentWorkflowRuntimeResult("plugin reply", List.of()));
        AgentAppService service = new AgentAppService(
                mock(CapabilityAppService.class), mock(CapabilityModuleRepo.class), applicationRepo,
                mock(AgentToolRepo.class), tools, mock(AgentModelCatalogParser.class), mock(WikiSpaceRepo.class),
                workflowRuntime, mock(AgentWorkflowValidator.class), permission -> true, runtimeApplications
        );

        assertThat(service.publishedApplications()).extracting(item -> item.getName())
                .containsExactly("消费 Agent", "插件机器人");
        assertThat(service.runByCode(BuiltinAgentCodes.LEGACY_GROUP_CHATBOT, new AgentRunCmd()).getContent())
                .isEqualTo("plugin reply");
        verify(workflowRuntime).execute(eq(runtime), any(), any(), any(), any(), any());
        verify(applicationRepo, never()).findByCode(BuiltinAgentCodes.LEGACY_GROUP_CHATBOT);
    }
}
