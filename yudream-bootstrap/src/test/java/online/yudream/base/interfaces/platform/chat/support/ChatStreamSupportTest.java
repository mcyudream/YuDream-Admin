package online.yudream.base.interfaces.platform.chat.support;

import online.yudream.base.application.platform.chat.cmd.ChatSendCmd;
import online.yudream.base.application.platform.chat.dto.ChatQuotaDTO;
import online.yudream.base.application.platform.chat.service.ChatAppService;
import online.yudream.base.application.platform.chat.service.ChatQuotaAppService;
import online.yudream.base.application.platform.chat.support.ChatDispatchResult;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.interfaces.platform.ai.res.AguiStreamEventRes;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class ChatStreamSupportTest {

    @Test
    void stopsCallbacksAndDoesNotSendFinishedAfterSendIOException() throws Exception {
        ChatAppService chatAppService = mock(ChatAppService.class);
        ChatQuotaAppService quotaService = mock(ChatQuotaAppService.class);
        List<String> eventTypes = new CopyOnWriteArrayList<>();
        CountDownLatch returned = new CountDownLatch(1);
        when(chatAppService.streamOnce(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            try {
                onDelta.accept("first");
                onDelta.accept("second");
                return ChatDispatchResult.of("finished", AiUsage.empty());
            }
            finally {
                returned.countDown();
            }
        });

        try (MockedConstruction<SseEmitter> construction = emitterConstruction(eventTypes, "TEXT_MESSAGE_CHUNK")) {
            support(chatAppService, quotaService).streamOnce(7L, command());

            assertThat(returned.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(eventTypes).containsExactly("RUN_STARTED", "TEXT_MESSAGE_CHUNK");
            assertThat(construction.constructed()).hasSize(1);
        }
    }

    @Test
    void completionCancelsRunningApplicationTask() throws Exception {
        ChatAppService chatAppService = mock(ChatAppService.class);
        ChatQuotaAppService quotaService = mock(ChatQuotaAppService.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicReference<Runnable> completion = new AtomicReference<>();
        when(chatAppService.streamOnce(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            started.countDown();
            try {
                Thread.sleep(30_000L);
                return ChatDispatchResult.of("unexpected", AiUsage.empty());
            }
            catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                interrupted.countDown();
                throw new IllegalStateException(error);
            }
        });

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class, (emitter, context) -> {
            doAnswer(invocation -> {
                completion.set(invocation.getArgument(0));
                return null;
            }).when(emitter).onCompletion(any());
        })) {
            support(chatAppService, quotaService).streamOnce(7L, command());

            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(completion.get()).isNotNull();
            completion.get().run();
            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void sendFailureWhileFinishingDoesNotProduceSecondTerminalEvent() throws Exception {
        ChatAppService chatAppService = mock(ChatAppService.class);
        ChatQuotaAppService quotaService = mock(ChatQuotaAppService.class);
        List<String> eventTypes = new CopyOnWriteArrayList<>();
        CountDownLatch terminalAttempted = new CountDownLatch(1);
        when(chatAppService.streamOnce(any(), any(), any(), any(), any(), any()))
                .thenReturn(ChatDispatchResult.of("answer", AiUsage.empty()));
        when(quotaService.me(7L)).thenReturn(new ChatQuotaDTO("7", LocalDate.now(), 0, 100, 100));

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class, (emitter, context) ->
                doAnswer(invocation -> {
                    AguiStreamEventRes event = event(invocation.getArgument(0));
                    eventTypes.add(event.getType());
                    if ("RUN_FINISHED".equals(event.getType())) {
                        terminalAttempted.countDown();
                        throw new IOException("client disconnected");
                    }
                    return null;
                }).when(emitter).send(any(SseEmitter.SseEventBuilder.class)))) {
            support(chatAppService, quotaService).streamOnce(7L, command());

            assertThat(terminalAttempted.await(2, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100L);
            assertThat(eventTypes.stream().filter(type -> type.startsWith("RUN_") && !"RUN_STARTED".equals(type)))
                    .containsExactly("RUN_FINISHED");
        }
    }

    @Test
    void streamOnceFinishedPayloadContainsAuthoritativeReasoning() throws Exception {
        ChatAppService chatAppService = mock(ChatAppService.class);
        ChatQuotaAppService quotaService = mock(ChatQuotaAppService.class);
        List<AguiStreamEventRes> events = new CopyOnWriteArrayList<>();
        CountDownLatch finished = new CountDownLatch(1);
        when(chatAppService.streamOnce(any(), any(), any(), any(), any(), any()))
                .thenReturn(ChatDispatchResult.of("answer", "authoritative reasoning", AiUsage.empty(), List.of()));
        when(quotaService.me(7L)).thenReturn(new ChatQuotaDTO("7", LocalDate.now(), 0, 100, 100));

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class, (emitter, context) ->
                doAnswer(invocation -> {
                    AguiStreamEventRes event = event(invocation.getArgument(0));
                    events.add(event);
                    if ("RUN_FINISHED".equals(event.getType())) finished.countDown();
                    return null;
                }).when(emitter).send(any(SseEmitter.SseEventBuilder.class)))) {
            support(chatAppService, quotaService).streamOnce(7L, command());

            assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
            AguiStreamEventRes terminal = events.stream()
                    .filter(event -> "RUN_FINISHED".equals(event.getType()))
                    .findFirst().orElseThrow();
            assertThat(((Map<?, ?>) terminal.getResult()).get("reasoning")).isEqualTo("authoritative reasoning");
        }
    }

    private static MockedConstruction<SseEmitter> emitterConstruction(List<String> eventTypes, String failingType) {
        return mockConstruction(SseEmitter.class, (emitter, context) ->
                doAnswer(invocation -> {
                    AguiStreamEventRes event = event(invocation.getArgument(0));
                    eventTypes.add(event.getType());
                    if (failingType.equals(event.getType())) throw new IOException("client disconnected");
                    return null;
                }).when(emitter).send(any(SseEmitter.SseEventBuilder.class)));
    }

    private static AguiStreamEventRes event(SseEmitter.SseEventBuilder builder) {
        return builder.build().stream()
                .map(item -> item.getData())
                .filter(AguiStreamEventRes.class::isInstance)
                .map(AguiStreamEventRes.class::cast)
                .findFirst().orElseThrow();
    }

    private static ChatStreamSupport support(ChatAppService appService, ChatQuotaAppService quotaService) {
        ChatStreamSupport support = new ChatStreamSupport(appService, quotaService);
        ReflectionTestUtils.setField(support, "timeout", Duration.ofSeconds(30));
        return support;
    }

    private static ChatSendCmd command() {
        ChatSendCmd command = new ChatSendCmd();
        command.setQuestion("question");
        return command;
    }
}
