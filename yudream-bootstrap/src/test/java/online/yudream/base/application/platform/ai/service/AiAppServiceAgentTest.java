package online.yudream.base.application.platform.ai.service;

import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.dto.AgentRunDTO;
import online.yudream.base.application.platform.agent.service.AgentAppService;
import online.yudream.base.application.platform.agent.service.BuiltinAgentCodes;
import online.yudream.base.application.platform.ai.cmd.CmsPageGenerateCmd;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAppServiceAgentTest {
    @Test void workspaceUsesTheCallerPermissionsAndRealProgressCallback() {
        var harness = mock(online.yudream.base.domain.platform.ai.service.HarnessAgentGateway.class);
        var repo = mock(online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo.class);
        var module = mock(online.yudream.base.domain.platform.capability.aggregate.CapabilityModule.class);
        when(module.enabled()).thenReturn(true);
        when(module.getConfig()).thenReturn(java.util.Map.of());
        when(repo.findByCode("ai")).thenReturn(java.util.Optional.of(module));
        when(harness.run(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            ((java.util.function.Consumer<online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress>) invocation.getArgument(4))
                    .accept(new online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress("tool-start", "工具"));
            return new online.yudream.base.domain.platform.ai.valobj.HarnessRunResult("完成", "", null, List.of());
        });
        var service = new AiAppService(mock(CapabilityAppService.class), mock(AgentAppService.class),
                mock(online.yudream.base.application.system.setting.service.SettingAppService.class), repo,
                mock(online.yudream.base.domain.platform.ai.service.AiGenerationGateway.class), harness, List.of());
        var progress = new java.util.ArrayList<String>();
        service.streamCmsWorkspace(7L, List.of("platform:ai:generate"), "home", "edit", List.of(), null, null,
                ignored -> {}, ignored -> {}, ignored -> {}, activity -> progress.add(activity.action()));
        var captured = ArgumentCaptor.forClass(online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest.class);
        verify(harness).run(captured.capture(), any(), any(), any(), any());
        assertThat(captured.getValue().permissionCodes()).containsExactly("platform:ai:generate");
        assertThat(progress).containsExactly("tool-start");
    }

    @Test
    void delegatesCmsGenerationToSelectedAgentWithRuntimeContext() {
        CapabilityAppService capabilities = mock(CapabilityAppService.class);
        AgentAppService agents = mock(AgentAppService.class);
        when(agents.debugByCode(eq(BuiltinAgentCodes.CMS_BUILDER), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AgentRunDTO.builder().content("完成").toolResults(List.of()).build());
        AiAppService service = new AiAppService(capabilities, agents,
                mock(online.yudream.base.application.system.setting.service.SettingAppService.class),
                mock(online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo.class),
                mock(online.yudream.base.domain.platform.ai.service.AiGenerationGateway.class), null, List.of());
        CmsPageGenerateCmd command = new CmsPageGenerateCmd();
        command.setAgentCode(BuiltinAgentCodes.CMS_BUILDER);
        command.setPrompt("创建首页");
        command.setCurrentHtml("<main></main>");
        command.setHistory(List.of(new AiChatMessage("user", "上一轮")));
        command.setPermissionCodes(List.of("platform:cms:edit"));
        command.setPermissionContextExplicit(true);

        var result = service.streamCmsPage(command, ignored -> {}, ignored -> {}, ignored -> {});

        assertThat(result.getSummary()).isEqualTo("完成");
        ArgumentCaptor<AgentRunCmd> agentCommand = ArgumentCaptor.forClass(AgentRunCmd.class);
        verify(agents).debugByCode(eq(BuiltinAgentCodes.CMS_BUILDER), agentCommand.capture(), any(), any(), any(), any(), any(), any());
        assertThat(agentCommand.getValue().isPermissionContextExplicit()).isTrue();
        assertThat(agentCommand.getValue().getPermissionCodes()).containsExactly("platform:cms:edit");
    }
}
