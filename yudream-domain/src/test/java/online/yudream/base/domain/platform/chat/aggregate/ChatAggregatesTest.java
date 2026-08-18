package online.yudream.base.domain.platform.chat.aggregate;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;
import online.yudream.base.domain.platform.chat.valobj.ChatAttachment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatAggregatesTest {

    @Test
    void sessionEnforcesUserIsolation() {
        ChatSession session = ChatSession.create(1L, "测试", ChatScopeType.GENERAL);

        session.belongsTo(1L);
        assertThrows(BizException.class, () -> session.belongsTo(2L));
    }

    @Test
    void quotaTracksRemainingAndUsage() {
        UserChatQuota quota = UserChatQuota.of(1L, LocalDate.of(2026, 8, 16), 100);

        assertEquals(100, quota.remaining());
        quota.add(new AiUsage(10, 20, 30));
        assertEquals(30, quota.getUsedTokens());
        assertEquals(70, quota.remaining());
        assertTrue(quota.exceed(new AiUsage(0, 0, 71)));
    }

    @Test
    void assistantMessageCanCompleteFailAndCancel() {
        ChatMessage message = ChatMessage.assistant(1L, 1L);
        assertEquals("STREAMING", message.getStatus().name());

        message.complete("回答", new AiUsage(1, 2, 3));
        assertEquals("回答", message.getContent());
        assertEquals(3, message.getUsage().totalTokens());
        assertEquals("COMPLETED", message.getStatus().name());

        message.cancel();
        assertEquals("COMPLETED", message.getStatus().name());
    }

    @Test
    void userMessageCarriesAttachments() {
        ChatMessage message = ChatMessage.user(1L, 1L, "看下这个文档", List.of(
                new ChatAttachment(10L, "a.pdf", "application/pdf", 12L, "DOCUMENT", "/a", "正文", null)));

        assertEquals(1, message.getAttachments().size());
        assertEquals("a.pdf", message.getAttachments().get(0).fileName());
    }
}
