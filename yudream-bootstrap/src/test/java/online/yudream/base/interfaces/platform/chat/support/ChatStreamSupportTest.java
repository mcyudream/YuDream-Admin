package online.yudream.base.interfaces.platform.chat.support;

import online.yudream.base.application.platform.ai.dto.AiRunDTO;
import online.yudream.base.application.platform.ai.service.AiRunAppService;
import online.yudream.base.application.platform.chat.cmd.ChatSendCmd;
import online.yudream.base.application.platform.chat.service.ChatRunAppService;
import online.yudream.base.domain.platform.ai.valobj.AiRunEvent;
import online.yudream.base.interfaces.platform.ai.res.AguiStreamEventRes;
import online.yudream.base.interfaces.platform.ai.support.AiRunStreamSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 连接仅订阅后端日志，浏览器关闭不得终止持久任务。 */
class ChatStreamSupportTest {
    @Test
    void legacyAddressStartsOneDurableRunAndSubscribesToItsJournal() {
        ChatRunAppService chat = mock(ChatRunAppService.class);
        AiRunStreamSupport streams = mock(AiRunStreamSupport.class);
        AiRunDTO receipt = mock(AiRunDTO.class);
        ChatSendCmd command = new ChatSendCmd();
        SseEmitter emitter = new SseEmitter();
        when(receipt.id()).thenReturn("run-1");
        when(chat.startOnce(7L, command)).thenReturn(receipt);
        when(streams.subscribe(7L, "run-1", 0, true)).thenReturn(emitter);

        assertThat(new ChatStreamSupport(chat, streams).streamOnce(7L, command)).isSameAs(emitter);
        verify(chat, times(1)).startOnce(7L, command);
        verify(streams).subscribe(7L, "run-1", 0, true);
    }

    @Test
    void sendFailureClosesObservationAndNeverCancelsBackendRun() throws Exception {
        AiRunAppService runs = mock(AiRunAppService.class);
        when(runs.events(7L, "run-1", 0)).thenReturn(List.of(event(1, "ai.message", "delta", Map.of("content", "first")),
                event(2, "ai.message", "delta", Map.of("content", "second"))));
        List<String> types = new CopyOnWriteArrayList<>();
        CountDownLatch closed = new CountDownLatch(1);
        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class, (emitter, context) -> {
            doAnswer(call -> {
                types.add(agui(call.getArgument(0)).getType());
                throw new IOException("client disconnected");
            }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
            doAnswer(call -> { closed.countDown(); return null; }).when(emitter).complete();
        })) {
            streams(runs).subscribe(7L, "run-1", 0, true);
            assertThat(closed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(types).containsExactly("TEXT_MESSAGE_CHUNK");
            verify(runs, never()).cancel(anyLong(), anyString());
        }
    }

    @Test
    void completionOnlyReleasesSubscription() throws Exception {
        AiRunAppService runs = mock(AiRunAppService.class);
        AtomicReference<Runnable> completion = new AtomicReference<>();
        CountDownLatch polled = new CountDownLatch(1);
        when(runs.events(7L, "run-1", 0)).thenAnswer(call -> { polled.countDown(); return List.of(); });
        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class, (emitter, context) ->
                doAnswer(call -> { completion.set(call.getArgument(0)); return null; }).when(emitter).onCompletion(any()))) {
            streams(runs).subscribe(7L, "run-1", 0, true);
            assertThat(polled.await(2, TimeUnit.SECONDS)).isTrue();
            completion.get().run();
            verify(runs, never()).cancel(anyLong(), anyString());
        }
    }

    @Test
    void reconnectUsesCommittedCursorAndPreservesAuthoritativeReasoning() throws Exception {
        AiRunAppService runs = mock(AiRunAppService.class);
        when(runs.events(7L, "run-1", 17)).thenReturn(List.of(event(18, "ai.result", "completed",
                Map.of("result", Map.of("content", "answer", "reasoning", "authoritative reasoning")))));
        when(runs.finished(7L, "run-1", 18)).thenReturn(true);
        List<AguiStreamEventRes> events = new CopyOnWriteArrayList<>();
        CountDownLatch closed = new CountDownLatch(1);
        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class, (emitter, context) -> {
            doAnswer(call -> { events.add(agui(call.getArgument(0))); return null; }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
            doAnswer(call -> { closed.countDown(); return null; }).when(emitter).complete();
        })) {
            streams(runs).subscribe(7L, "run-1", 17, true);
            assertThat(closed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(events).hasSize(1);
            assertThat(events.getFirst().getType()).isEqualTo("RUN_FINISHED");
            assertThat(((Map<?, ?>) events.getFirst().getResult()).get("reasoning")).isEqualTo("authoritative reasoning");
            verify(runs).events(7L, "run-1", 17);
        }
    }

    @Test
    void terminalSendFailureDoesNotProduceSecondTerminalEvent() throws Exception {
        AiRunAppService runs = mock(AiRunAppService.class);
        when(runs.events(7L, "run-1", 0)).thenReturn(List.of(event(1, "ai.error", "failed", Map.of("message", "provider failure"))));
        List<String> types = new CopyOnWriteArrayList<>();
        CountDownLatch closed = new CountDownLatch(1);
        try (MockedConstruction<SseEmitter> ignored = mockConstruction(SseEmitter.class, (emitter, context) -> {
            doAnswer(call -> { types.add(agui(call.getArgument(0)).getType()); throw new IOException("client disconnected"); })
                    .when(emitter).send(any(SseEmitter.SseEventBuilder.class));
            doAnswer(call -> { closed.countDown(); return null; }).when(emitter).complete();
        })) {
            streams(runs).subscribe(7L, "run-1", 0, true);
            assertThat(closed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(types).containsExactly("RUN_ERROR");
            verify(runs, never()).cancel(anyLong(), anyString());
        }
    }

    private static AiRunStreamSupport streams(AiRunAppService runs) {
        AiRunStreamSupport support = new AiRunStreamSupport(runs);
        ReflectionTestUtils.setField(support, "timeout", Duration.ofSeconds(30));
        return support;
    }
    private static AiRunEvent event(long cursor, String type, String action, Map<String, Object> payload) {
        return new AiRunEvent("run-1", cursor, type, action, "chat", "run-1", Instant.now(), payload);
    }
    private static AguiStreamEventRes agui(SseEmitter.SseEventBuilder builder) {
        return builder.build().stream().map(item -> item.getData()).filter(AguiStreamEventRes.class::isInstance)
                .map(AguiStreamEventRes.class::cast).findFirst().orElseThrow();
    }
}
