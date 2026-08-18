package online.yudream.base.interfaces.platform.wiki.support;

import online.yudream.base.application.platform.wiki.dto.WikiChatResultDTO;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WikiChatExecutionTest {

    private final WikiChatExecution execution = new WikiChatExecution();

    @Test
    void completionCancelsBlockingGeneration() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        SseEmitter emitter = new SseEmitter();
        WikiChatExecution.Started running = execution.start(emitter, 1L,
                (delta, reasoning, tool, activity) -> blocking(started, interrupted), true);

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        invokeCallback(emitter, "completionCallback", "run");

        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(running.future()).isCancelled();
    }

    @Test
    void timeoutCancelsBlockingGeneration() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        SseEmitter emitter = new SseEmitter();
        WikiChatExecution.Started running = execution.start(emitter, 1L,
                (delta, reasoning, tool, activity) -> blocking(started, interrupted), true);

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        invokeCallback(emitter, "timeoutCallback", "run");

        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(running.future()).isCancelled();
    }

    @Test
    void sendIOExceptionCancelsGenerationAndStopsFurtherCallbacks() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        FailingEmitter emitter = new FailingEmitter();
        WikiChatExecution.Started running = execution.start(emitter, 1L,
                (delta, reasoning, tool, activity) -> {
                    try {
                        delta.accept("first");
                        reasoning.accept("must-not-run");
                    } catch (RuntimeException exception) {
                        if (Thread.currentThread().isInterrupted()) {
                            interrupted.countDown();
                        }
                        throw exception;
                    }
                    return new WikiChatResultDTO("answer", List.of());
                }, false);

        assertThatThrownByFuture(running);
        assertThat(running.future()).isCancelled();
        assertThat(emitter.sendCount).isEqualTo(1);
    }

    private WikiChatResultDTO blocking(CountDownLatch started, CountDownLatch interrupted) {
        started.countDown();
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            interrupted.countDown();
        }
        return new WikiChatResultDTO("", List.of());
    }

    private void assertThatThrownByFuture(WikiChatExecution.Started running) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!running.future().isDone() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(running.future()).isDone();
    }

    private void invokeCallback(SseEmitter emitter, String fieldName, String methodName) throws Exception {
        Field field = ResponseBodyEmitter.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Object callback = field.get(emitter);
        Method method = callback.getClass().getMethod(methodName);
        method.setAccessible(true);
        method.invoke(callback);
    }

    private static final class FailingEmitter extends SseEmitter {
        private int sendCount;

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            sendCount++;
            throw new IOException("client disconnected");
        }
    }
}
