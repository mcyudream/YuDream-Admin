package online.yudream.base.application.platform.chat.support;

import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.dto.AgentRunDTO;
import online.yudream.base.application.platform.agent.service.AgentAppService;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatDispatcherPermissionTest {

    @Test
    void dispatchPassesImmutableExplicitPermissionSnapshotToAgent() {
        AgentAppService agentAppService = mock(AgentAppService.class);
        when(agentAppService.debugByCode(eq("wiki-agent"), any(), any(), any(), any(), any()))
                .thenReturn(AgentRunDTO.builder().content("ok").toolResults(List.of()).usage(AiUsage.empty()).build());
        AgentChatDispatcher dispatcher = new AgentChatDispatcher(agentAppService);
        List<String> permissions = new ArrayList<>(List.of("platform:wiki:search"));

        dispatcher.dispatch(context(permissions));
        permissions.clear();

        ArgumentCaptor<AgentRunCmd> commandCaptor = ArgumentCaptor.forClass(AgentRunCmd.class);
        verify(agentAppService).debugByCode(eq("wiki-agent"), commandCaptor.capture(), any(), any(), any(), any());
        AgentRunCmd command = commandCaptor.getValue();
        assertThat(command.getPermissionCodes()).containsExactly("platform:wiki:search");
        assertThat(command.isPermissionContextExplicit()).isTrue();
        assertThatThrownBy(() -> command.getPermissionCodes().add("platform:wiki:edit"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void dispatchMarksEmptyPermissionSnapshotExplicit() {
        AgentAppService agentAppService = mock(AgentAppService.class);
        when(agentAppService.debugByCode(eq("wiki-agent"), any(), any(), any(), any(), any()))
                .thenReturn(AgentRunDTO.builder().content("ok").toolResults(List.of()).usage(AiUsage.empty()).build());
        AgentChatDispatcher dispatcher = new AgentChatDispatcher(agentAppService);

        dispatcher.dispatch(context(List.of()));

        ArgumentCaptor<AgentRunCmd> commandCaptor = ArgumentCaptor.forClass(AgentRunCmd.class);
        verify(agentAppService).debugByCode(eq("wiki-agent"), commandCaptor.capture(), any(), any(), any(), any());
        assertThat(commandCaptor.getValue().getPermissionCodes()).isEmpty();
        assertThat(commandCaptor.getValue().isPermissionContextExplicit()).isTrue();
    }

    private ChatDispatchContext context(List<String> permissionCodes) {
        return new ChatDispatchContext(
                "question",
                null,
                null,
                "wiki-agent",
                null,
                List.of(),
                List.of(),
                permissionCodes,
                ignored -> {
                },
                ignored -> {
                },
                ignored -> {
                },
                ignored -> {
                });
    }
}
