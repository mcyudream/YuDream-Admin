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
    }

    @Test
    void importsPluginRuntimeAsEditableLocalDraft() {
        AgentApplicationRepo applicationRepo = mock(AgentApplicationRepo.class);
        AgentRuntimeApplicationRegistry runtimeApplications = mock(AgentRuntimeApplicationRegistry.class);
        AgentApplication runtime = AgentApplication.builder()
                .id(-1L).code("plugin-agent").name("插件 Agent").description("默认编排")
                .workflowJson("{}").toolCodes(List.of("web.fetch")).status(AgentApplicationStatus.PUBLISHED).build();
        when(runtimeApplications.findByCode("plugin-agent")).thenReturn(java.util.Optional.of(runtime));
        when(runtimeApplications.ownerCode("plugin-agent")).thenReturn(java.util.Optional.of("sample-plugin"));
        when(applicationRepo.findByCode("plugin-agent")).thenReturn(java.util.Optional.empty());
        when(applicationRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        @SuppressWarnings("unchecked") ObjectProvider<AiAgentTool> tools = mock(ObjectProvider.class);
        when(tools.stream()).thenReturn(Stream.empty());
        AgentAppService service = new AgentAppService(
                mock(CapabilityAppService.class), mock(CapabilityModuleRepo.class), applicationRepo,
                mock(AgentToolRepo.class), tools, mock(AgentModelCatalogParser.class), mock(WikiSpaceRepo.class),
                mock(AgentWorkflowRuntimeService.class), mock(AgentWorkflowValidator.class), permission -> true, runtimeApplications
        );

        var imported = service.importRuntimeApplication("plugin-agent");

        assertThat(imported.getCode()).isEqualTo("plugin-agent");
        assertThat(imported.getSourcePluginCode()).isEqualTo("sample-plugin");
        assertThat(imported.getStatus()).isEqualTo(AgentApplicationStatus.DRAFT);
        verify(applicationRepo).save(any(AgentApplication.class));
    }

    @Test
    void publishedPluginOverrideTakesPrecedenceOverRuntimeDefault() {
        AgentApplicationRepo applicationRepo = mock(AgentApplicationRepo.class);
        AgentWorkflowRuntimeService workflowRuntime = mock(AgentWorkflowRuntimeService.class);
        AgentRuntimeApplicationRegistry runtimeApplications = mock(AgentRuntimeApplicationRegistry.class);
        AgentApplication runtime = AgentApplication.builder()
                .id(-1L).code("plugin-agent").name("插件 Agent").workflowJson("{}").toolCodes(List.of())
                .status(AgentApplicationStatus.PUBLISHED).build();
        AgentApplication override = AgentApplication.pluginOverride("sample-plugin", runtime);
        override.setId(9L);
        override.publish();
        when(runtimeApplications.findByCode("plugin-agent")).thenReturn(java.util.Optional.of(runtime));
        when(applicationRepo.findByCode("plugin-agent")).thenReturn(java.util.Optional.of(override));
        when(workflowRuntime.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AgentWorkflowRuntimeResult("local reply", List.of()));
        @SuppressWarnings("unchecked") ObjectProvider<AiAgentTool> tools = mock(ObjectProvider.class);
        when(tools.stream()).thenReturn(Stream.empty());
        AgentAppService service = new AgentAppService(
                mock(CapabilityAppService.class), mock(CapabilityModuleRepo.class), applicationRepo,
                mock(AgentToolRepo.class), tools, mock(AgentModelCatalogParser.class), mock(WikiSpaceRepo.class),
                workflowRuntime, mock(AgentWorkflowValidator.class), permission -> true, runtimeApplications
        );

        assertThat(service.runByCode("plugin-agent", new AgentRunCmd()).getContent()).isEqualTo("local reply");
        verify(workflowRuntime).execute(eq(override), any(), any(), any(), any(), any());
    }
}
