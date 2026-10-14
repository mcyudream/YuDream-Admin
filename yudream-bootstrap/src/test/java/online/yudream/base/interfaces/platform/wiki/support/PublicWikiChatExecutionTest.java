package online.yudream.base.interfaces.platform.wiki.support;

import online.yudream.base.application.platform.wiki.dto.PublicWikiChatRunDTO;
import online.yudream.base.application.platform.wiki.service.PublicWikiChatRunAppService;
import online.yudream.base.application.platform.wiki.service.WikiPublicAppService;
import online.yudream.base.domain.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 公开提问租约属于后台运行，页面关闭不归还正在消耗模型的并发名额。 */
class PublicWikiChatExecutionTest {
    @Test void permitRemainsHeldUntilBackendCompletesAndIsReleasedOnce() {
        var limiter = limiter();
        var chat = mock(PublicWikiChatRunAppService.class);
        AtomicReference<Runnable> release = new AtomicReference<>();
        when(chat.start(eq("docs"), isNull(), eq("question"), anyList(), any())).thenAnswer(invocation -> {
            release.set(invocation.getArgument(4)); return mock(PublicWikiChatRunDTO.class);
        });
        var execution = new PublicWikiChatRunSupport(limiter, mock(WikiPublicAppService.class), chat, 2);
        execution.start("1.2.3.4", "docs", null, "question", List.of());
        assertThatThrownBy(() -> execution.start("1.2.3.4", "docs", null, "question", List.of())).isInstanceOf(BizException.class);
        release.get().run(); release.get().run();
        execution.start("1.2.3.4", "docs", null, "question", List.of());
        verify(chat, times(2)).start(eq("docs"), isNull(), eq("question"), anyList(), any());
        release.get().run();
    }

    @Test void submissionFailureReleasesBothGlobalAndPerClientCapacity() {
        var chat = mock(PublicWikiChatRunAppService.class);
        when(chat.start(anyString(), isNull(), anyString(), anyList(), any())).thenThrow(new BizException("模型不可用"));
        var execution = new PublicWikiChatRunSupport(limiter(), mock(WikiPublicAppService.class), chat, 1);
        assertThatThrownBy(() -> execution.start("1.2.3.4", "docs", null, "q", List.of())).hasMessage("模型不可用");
        assertThatThrownBy(() -> execution.start("1.2.3.4", "docs", null, "q", List.of())).hasMessage("模型不可用");
    }

    @Test void nonexistentPublicSpaceDoesNotAllocateLimiterKeys() {
        var spaces = mock(WikiPublicAppService.class);
        doThrow(new BizException("知识库不存在")).when(spaces).ensurePublicSpace("fake");
        var limiter = limiter();
        var execution = new PublicWikiChatRunSupport(limiter, spaces, mock(PublicWikiChatRunAppService.class), 1);
        assertThatThrownBy(() -> execution.start("1.2.3.4", "fake", null, "q", List.of())).isInstanceOf(BizException.class);
        assertThat(limiter.trackedKeys()).isZero();
    }

    private PublicWikiChatRateLimiter limiter() { return new PublicWikiChatRateLimiter(Clock.systemUTC(), 100, Duration.ofMinutes(1), 1); }
}
