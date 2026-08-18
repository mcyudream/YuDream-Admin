package online.yudream.base.interfaces.platform.wiki.support;

import online.yudream.base.application.platform.wiki.dto.WikiChatResultDTO;
import online.yudream.base.domain.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

class PublicWikiChatExecutionTest {

    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void normalCompletionDoesNotSpuriouslyCancelAndReleasesPermit() throws Exception {
        PublicWikiChatRateLimiter limiter = limiter(1);
        PublicWikiChatExecution execution = execution(limiter, 1);

        PublicWikiChatExecution.Started started = execution.start(
                "1.2.3.4",
                "docs",
                (delta, reasoning, tool, activity) -> {
                    delta.accept("hello");
                    return new WikiChatResultDTO("hello", List.of());
                });

        started.future().get(5, TimeUnit.SECONDS);
        assertThat(started.future()).isDone().isNotCancelled();

        // 模拟容器在正常完成后触发 onCompletion：任务已标记完成，不得反向取消。
        invokeCompletion(started.emitter());

        assertThat(started.future()).isNotCancelled();
        // 租约已在任务 finally 中释放，可再次获取。
        limiter.acquire("1.2.3.4", "docs").close();
    }

    @Test
    void timeoutCallbackCancelsBlockingTaskAndReleasesPermit() throws Exception {
        PublicWikiChatRateLimiter limiter = limiter(1);
        PublicWikiChatExecution execution = execution(limiter, 1);

        CountDownLatch startedTask = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        PublicWikiChatExecution.Started started = execution.start(
                "1.2.3.4",
                "docs",
                blockingWork(startedTask, interrupted));

        assertThat(startedTask.await(5, TimeUnit.SECONDS)).isTrue();
        invokeTimeout(started.emitter());

        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(started.future()).isCancelled();
        awaitPermitAvailable(limiter);
    }

    @Test
    void errorCallbackCancelsBlockingTaskAndReleasesPermit() throws Exception {
        PublicWikiChatRateLimiter limiter = limiter(1);
        PublicWikiChatExecution execution = execution(limiter, 1);

        CountDownLatch startedTask = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        PublicWikiChatExecution.Started started = execution.start(
                "1.2.3.4",
                "docs",
                blockingWork(startedTask, interrupted));

        assertThat(startedTask.await(5, TimeUnit.SECONDS)).isTrue();
        invokeError(started.emitter(), new RuntimeException("client gone"));

        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(started.future()).isCancelled();
        awaitPermitAvailable(limiter);
    }

    @Test
    void cancellingQueuedTaskBeforeStartReleasesPermit() throws Exception {
        PublicWikiChatRateLimiter limiter = limiter(2);
        execution(limiter, 1, 1);

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        PublicWikiChatExecution.Started first = new PublicWikiChatExecution(executor, limiter, Duration.ofSeconds(30)).start(
                "1.2.3.4", "docs", (delta, reasoning, tool, activity) -> {
                    firstStarted.countDown();
                    await(releaseFirst);
                    return new WikiChatResultDTO("", List.of());
                });
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

        CountDownLatch queuedWorkStarted = new CountDownLatch(1);
        PublicWikiChatExecution execution = new PublicWikiChatExecution(executor, limiter, Duration.ofSeconds(30));
        PublicWikiChatExecution.Started queued = execution.start("1.2.3.4", "docs", (delta, reasoning, tool, activity) -> {
            queuedWorkStarted.countDown();
            return new WikiChatResultDTO("", List.of());
        });
        invokeCompletion(queued.emitter());

        assertThat(queued.future()).isCancelled();
        PublicWikiChatRateLimiter.Permit replacement = limiter.acquire("1.2.3.4", "docs");
        replacement.close();
        releaseFirst.countDown();
        first.future().get(5, TimeUnit.SECONDS);
        assertThat(queuedWorkStarted.await(100, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test
    void executorRejectionClosesPermitAndCompletesEmitterWithError() throws Exception {
        PublicWikiChatRateLimiter limiter = limiter(2);
        PublicWikiChatExecution execution = execution(limiter, 1);

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        PublicWikiChatExecution.Started first = execution.start(
                "1.2.3.4",
                "docs",
                (delta, reasoning, tool, activity) -> {
                    firstStarted.countDown();
                    await(releaseFirst);
                    return new WikiChatResultDTO("", List.of());
                });

        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> execution.start("1.2.3.4", "docs", (delta, reasoning, tool, activity) -> {
            return new WikiChatResultDTO("", List.of());
        })).isInstanceOf(RejectedExecutionException.class);

        // 拒绝路径已归还名额：仍有一个名额可供第三个请求占用（first 仍占用另一个）。
        limiter.acquire("1.2.3.4", "docs").close();

        releaseFirst.countDown();
        first.future().get(5, TimeUnit.SECONDS);
    }

    private PublicWikiChatExecution execution(PublicWikiChatRateLimiter limiter, int maxThreads) {
        return execution(limiter, maxThreads, 0);
    }

    private PublicWikiChatExecution execution(PublicWikiChatRateLimiter limiter, int maxThreads, int queueCapacity) {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(maxThreads);
        executor.setMaxPoolSize(maxThreads);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("test-public-wiki-chat-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return new PublicWikiChatExecution(executor, limiter, Duration.ofSeconds(30));
    }

    private PublicWikiChatRateLimiter limiter(int maxConcurrent) {
        return new PublicWikiChatRateLimiter(Clock.systemUTC(), 100, Duration.ofMinutes(1), maxConcurrent);
    }

    private PublicWikiChatExecution.ChatWork blockingWork(CountDownLatch startedTask, CountDownLatch interrupted) {
        return (delta, reasoning, tool, activity) -> {
            startedTask.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                interrupted.countDown();
            }
            return new WikiChatResultDTO("", List.of());
        };
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void awaitPermitAvailable(PublicWikiChatRateLimiter limiter) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                limiter.acquire("1.2.3.4", "docs").close();
                return;
            } catch (BizException ignored) {
                Thread.sleep(20L);
            }
        }
        fail("permit was not released");
    }

    private void invokeCompletion(SseEmitter emitter) throws Exception {
        invokeCallback(emitter, "completionCallback", "run");
    }

    private void invokeTimeout(SseEmitter emitter) throws Exception {
        invokeCallback(emitter, "timeoutCallback", "run");
    }

    private void invokeError(SseEmitter emitter, Throwable error) throws Exception {
        Object callback = callbackField(emitter, "errorCallback");
        Method method = callback.getClass().getMethod("accept", Throwable.class);
        method.setAccessible(true);
        method.invoke(callback, error);
    }

    private void invokeCallback(SseEmitter emitter, String fieldName, String methodName) throws Exception {
        Object callback = callbackField(emitter, fieldName);
        Method method = callback.getClass().getMethod(methodName);
        method.setAccessible(true);
        method.invoke(callback);
    }

    private Object callbackField(SseEmitter emitter, String fieldName) throws Exception {
        Field field = ResponseBodyEmitter.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(emitter);
    }
}
