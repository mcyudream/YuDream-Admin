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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatSessionAppServiceTest {

    @Test
    void updateRejectsSessionOwnedByAnotherUser() {
        CapabilityAppService capabilities = mock(CapabilityAppService.class);
        ChatSessionRepo sessions = mock(ChatSessionRepo.class);
        ChatMessageRepo messages = mock(ChatMessageRepo.class);
        ChatSession session = ChatSession.create(2L, "别人的会话", ChatScopeType.GENERAL);
        session.setId(9L);
        when(sessions.findById(9L)).thenReturn(Optional.of(session));
        ChatSessionAppService service = new ChatSessionAppService(capabilities, sessions, messages);
        ChatSessionSaveCmd cmd = new ChatSessionSaveCmd();
        cmd.setTitle("改名");

        assertThatThrownBy(() -> service.update(1L, cmd)).isInstanceOf(BizException.class);
    }
}
