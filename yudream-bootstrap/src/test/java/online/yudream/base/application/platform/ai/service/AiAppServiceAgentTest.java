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

    @Test
    void delegatesCmsGenerationToSelectedAgentWithRuntimeContext() {
        CapabilityAppService capabilities = mock(CapabilityAppService.class);
        AgentAppService agents = mock(AgentAppService.class);
        when(agents.debugByCode(eq(BuiltinAgentCodes.CMS_BUILDER), any(), any(), any(), any()))
                .thenReturn(AgentRunDTO.builder().content("完成").toolResults(List.of()).build());
        AiAppService service = new AiAppService(capabilities, agents);
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
        verify(agents).debugByCode(eq(BuiltinAgentCodes.CMS_BUILDER), agentCommand.capture(), any(), any(), any());
        assertThat(agentCommand.getValue().isPermissionContextExplicit()).isTrue();
        assertThat(agentCommand.getValue().getPermissionCodes()).containsExactly("platform:cms:edit");
    }
}
