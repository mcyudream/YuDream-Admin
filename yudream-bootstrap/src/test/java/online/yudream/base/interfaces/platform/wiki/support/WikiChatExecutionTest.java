package online.yudream.base.interfaces.platform.wiki.support;

import online.yudream.base.application.platform.ai.service.AiRunAppService;
import online.yudream.base.interfaces.platform.ai.support.AiRunStreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Wiki 与所有 AI 入口消费同一日志订阅，浏览器结束订阅不再中断生成。 */
class WikiChatExecutionTest {
    @Test void completionOnlyClosesTheSubscription() throws Exception { checkClose("completionCallback"); }
    @Test void timeoutOnlyClosesTheSubscription() throws Exception { checkClose("timeoutCallback"); }

    private void checkClose(String callbackName) throws Exception {
        var runs = mock(AiRunAppService.class);
        var observed = new CountDownLatch(1);
        when(runs.events(7L, "wiki-run", 3L)).thenAnswer(invocation -> { observed.countDown(); return List.of(); });
        var streams = new AiRunStreamSupport(runs);
        ReflectionTestUtils.setField(streams, "timeout", Duration.ofMinutes(1));
        SseEmitter emitter = streams.subscribe(7L, "wiki-run", 3L, true);
        assertTrue(observed.await(3, TimeUnit.SECONDS));
        Field field = ResponseBodyEmitter.class.getDeclaredField(callbackName); field.setAccessible(true);
        Object callback = field.get(emitter);
        Method run = callback.getClass().getMethod("run"); run.setAccessible(true); run.invoke(callback);
        verify(runs, never()).cancel(anyLong(), anyString());
        verify(runs, never()).start(any(), any(), any(), any(), any());
        verify(runs).get(7L, "wiki-run");
    }
}
