package online.yudream.base.application.platform.chat.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.chat.cmd.ChatSessionSaveCmd;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.chat.aggregate.ChatSession;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;
import online.yudream.base.domain.platform.chat.repo.ChatMessageRepo;
import online.yudream.base.domain.platform.chat.repo.ChatSessionRepo;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class ChatSessionAppServiceTest {

    @Test
    void updateRejectsSessionOwnedByAnotherUser() {
        CapabilityAppService capabilities = mock(CapabilityAppService.class);
        ChatSessionRepo sessions = mock(ChatSessionRepo.class);
        ChatMessageRepo messages = mock(ChatMessageRepo.class);
        ChatSession session = ChatSession.create(2L, "别人的会话", ChatScopeType.GENERAL);
        session.setId(9L);
        when(sessions.findById(9L)).thenReturn(Optional.of(session));
        ChatSessionAppService service = new ChatSessionAppService(capabilities, sessions, messages,
                mock(online.yudream.base.domain.platform.ai.repo.AiRunRepo.class));
        ChatSessionSaveCmd cmd = new ChatSessionSaveCmd();
        cmd.setId(9L);
        cmd.setTitle("改名");

        assertThatThrownBy(() -> service.update(1L, cmd)).isInstanceOf(BizException.class).hasMessageContaining("无权");
    }
    @Test
    void renamingPreservesTheStatefulAgentAndModelSelection() {
        ChatSessionRepo sessions = mock(ChatSessionRepo.class);
        ChatSession session = ChatSession.create(1L, "工作流", ChatScopeType.AGENT);
        session.setId(9L); session.setAgentCode("workflow"); session.setProviderCode("provider"); session.setModelCode("model");
        when(sessions.findById(9L)).thenReturn(Optional.of(session));
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        var service = new ChatSessionAppService(mock(CapabilityAppService.class),sessions,mock(ChatMessageRepo.class),
                mock(online.yudream.base.domain.platform.ai.repo.AiRunRepo.class));
        ChatSessionSaveCmd cmd = new ChatSessionSaveCmd(); cmd.setId(9L); cmd.setTitle("新名称");
        service.update(1L,cmd);
        assertThat(session.getAgentCode()).isEqualTo("workflow"); assertThat(session.getProviderCode()).isEqualTo("provider");
        assertThat(session.getModelCode()).isEqualTo("model"); assertThat(session.getScopeType()).isEqualTo(ChatScopeType.AGENT);
    }
    @Test
    void deletingSessionWithDurableActiveRunCannotEraseItsMessages() {
        ChatSessionRepo sessions = mock(ChatSessionRepo.class); ChatMessageRepo messages = mock(ChatMessageRepo.class);
        var runs = mock(online.yudream.base.domain.platform.ai.repo.AiRunRepo.class);
        ChatSession session = ChatSession.create(1L,"会话",ChatScopeType.GENERAL); session.setId(9L);
        when(sessions.findById(9L)).thenReturn(Optional.of(session));
        when(runs.list(1L,"9",true)).thenReturn(java.util.List.of(online.yudream.base.domain.platform.ai.aggregate.AiRun.start(1L,"9","chat","instance")));
        var service = new ChatSessionAppService(mock(CapabilityAppService.class),sessions,messages,runs);
        assertThatThrownBy(()->service.delete(1L,9L)).isInstanceOf(BizException.class).hasMessageContaining("先取消");
        verify(messages,never()).deleteBySessionId(9L); verify(sessions,never()).deleteById(9L);
    }
}
