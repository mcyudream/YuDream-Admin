package online.yudream.base.application.platform.chat.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.chat.cmd.ChatSendCmd;
import online.yudream.base.application.platform.chat.dto.ChatContextRefDTO;
import online.yudream.base.application.platform.chat.support.ChatDispatchContext;
import online.yudream.base.application.platform.chat.support.ChatDispatchResult;
import online.yudream.base.application.platform.chat.support.ChatDispatcher;
import online.yudream.base.application.platform.chat.support.ChatWikiContextResolver;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.chat.aggregate.ChatMessage;
import online.yudream.base.domain.platform.chat.aggregate.ChatSession;
import online.yudream.base.domain.platform.chat.aggregate.UserChatQuota;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;
import online.yudream.base.domain.platform.chat.repo.ChatMessageRepo;
import online.yudream.base.domain.platform.chat.repo.ChatSessionRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatAppServiceWikiContextTest {

    @Test
    void accumulatesAndPersistsReasoningWithoutCreatingActivities() {
        CapabilityAppService capabilities = mock(CapabilityAppService.class);
        ChatSessionRepo sessions = mock(ChatSessionRepo.class);
        ChatMessageRepo messages = mock(ChatMessageRepo.class);
        ChatQuotaAppService quotas = mock(ChatQuotaAppService.class);
        ChatWikiContextResolver resolver = mock(ChatWikiContextResolver.class);
        List<ChatMessage> savedMessages = new ArrayList<>();
        AtomicLong ids = new AtomicLong(10);
        when(sessions.save(any())).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            if (session.getId() == null) session.setId(ids.incrementAndGet());
            return session;
        });
        when(messages.save(any())).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            if (message.getId() == null) message.setId(ids.incrementAndGet());
            savedMessages.add(message);
            return message;
        });
        when(messages.findBySessionId(any())).thenReturn(List.of());
        when(quotas.todayQuota(7L)).thenReturn(UserChatQuota.of(7L, LocalDate.now(), 1000));
        when(quotas.recordUsage(7L, 0)).thenReturn(UserChatQuota.of(7L, LocalDate.now(), 1000));
        when(resolver.resolve(any(), any(), any(), any()))
                .thenReturn(new ChatWikiContextResolver.ResolvedContext("", List.of()));
        ChatDispatcher dispatcher = new ChatDispatcher() {
            @Override public ChatScopeType scopeType() { return ChatScopeType.GENERAL; }
            @Override public ChatDispatchResult dispatch(ChatDispatchContext context) {
                context.onReasoningDelta().accept("分析一");
                context.onReasoningDelta().accept("\n分析二");
                return ChatDispatchResult.of("回答\n\n结束", AiUsage.empty());
            }
        };
        ChatAppService service = new ChatAppService(capabilities, sessions, messages, quotas, List.of(dispatcher), resolver);
        ChatSendCmd command = new ChatSendCmd();
        command.setScopeType(ChatScopeType.GENERAL);
        command.setQuestion("用户问题");
        List<String> streamedReasoning = new ArrayList<>();
        List<Object> activities = new ArrayList<>();

        var result = service.send(7L, command, null, streamedReasoning::add, null, activities::add);

        ChatMessage assistant = savedMessages.stream()
                .filter(message -> message.getRole() == online.yudream.base.domain.platform.chat.enumerate.ChatMessageRole.ASSISTANT)
                .findFirst().orElseThrow();
        assertThat(streamedReasoning).containsExactly("分析一", "\n分析二");
        assertThat(activities).isEmpty();
        assertThat(assistant.getReasoning()).isEqualTo("分析一\n分析二");
        assertThat(result.reasoning()).isEqualTo("分析一\n分析二");
        assertThat(result.content()).isEqualTo("回答\n\n结束");
    }

    @Test
    void streamOnceReturnsAccumulatedReasoning() {
        CapabilityAppService capabilities = mock(CapabilityAppService.class);
        ChatSessionRepo sessions = mock(ChatSessionRepo.class);
        ChatMessageRepo messages = mock(ChatMessageRepo.class);
        ChatQuotaAppService quotas = mock(ChatQuotaAppService.class);
        ChatWikiContextResolver resolver = mock(ChatWikiContextResolver.class);
        when(quotas.todayQuota(7L)).thenReturn(UserChatQuota.of(7L, LocalDate.now(), 1000));
        when(quotas.recordUsage(7L, 0)).thenReturn(UserChatQuota.of(7L, LocalDate.now(), 1000));
        when(resolver.resolve(any(), any(), any(), any()))
                .thenReturn(new ChatWikiContextResolver.ResolvedContext("", List.of()));
        ChatDispatcher dispatcher = new ChatDispatcher() {
            @Override public ChatScopeType scopeType() { return ChatScopeType.GENERAL; }
            @Override public ChatDispatchResult dispatch(ChatDispatchContext context) {
                context.onReasoningDelta().accept("reasoning-");
                context.onReasoningDelta().accept("final");
                return ChatDispatchResult.of("answer", AiUsage.empty());
            }
        };
        ChatAppService service = new ChatAppService(capabilities, sessions, messages, quotas, List.of(dispatcher), resolver);
        ChatSendCmd command = new ChatSendCmd();
        command.setQuestion("question");

        ChatDispatchResult result = service.streamOnce(7L, command, null, null, null, null);

        assertThat(result.reasoning()).isEqualTo("reasoning-final");
    }

    @Test
    void aggregatesConcurrentReasoningToolsAndActivitiesIntoConsistentSnapshot() throws Exception {
        CapabilityAppService capabilities = mock(CapabilityAppService.class);
        ChatSessionRepo sessions = mock(ChatSessionRepo.class);
        ChatMessageRepo messages = mock(ChatMessageRepo.class);
        ChatQuotaAppService quotas = mock(ChatQuotaAppService.class);
        ChatWikiContextResolver resolver = mock(ChatWikiContextResolver.class);
        List<ChatMessage> savedMessages = new ArrayList<>();
        AtomicLong ids = new AtomicLong(20);
        when(sessions.save(any())).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            if (session.getId() == null) session.setId(ids.incrementAndGet());
            return session;
        });
        when(messages.save(any())).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            if (message.getId() == null) message.setId(ids.incrementAndGet());
            savedMessages.add(message);
            return message;
        });
        when(messages.findBySessionId(any())).thenReturn(List.of());
        when(quotas.todayQuota(7L)).thenReturn(UserChatQuota.of(7L, LocalDate.now(), 100_000));
        when(quotas.recordUsage(7L, 0)).thenReturn(UserChatQuota.of(7L, LocalDate.now(), 100_000));
        when(resolver.resolve(any(), any(), any(), any()))
                .thenReturn(new ChatWikiContextResolver.ResolvedContext("", List.of()));
        int callbackCount = 300;
        ChatDispatcher dispatcher = new ChatDispatcher() {
            @Override public ChatScopeType scopeType() { return ChatScopeType.GENERAL; }
            @Override public ChatDispatchResult dispatch(ChatDispatchContext context) {
                CountDownLatch ready = new CountDownLatch(callbackCount * 3);
                CountDownLatch start = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(12)) {
                    IntStream.range(0, callbackCount).forEach(index -> {
                        executor.submit(() -> runCallback(ready, start,
                                () -> context.onReasoningDelta().accept("r" + index + ";")));
                        executor.submit(() -> runCallback(ready, start,
                                () -> context.onTool().accept(new online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult(
                                        "tool-" + index, "run", "permission", "done", Map.of("index", index)))));
                        executor.submit(() -> runCallback(ready, start,
                                () -> context.onActivity().accept(new online.yudream.base.domain.platform.chat.valobj.ChatActivity(
                                        "search", "phase-" + index, "running", "title", "content", null, null, null))));
                    });
                    try {
                        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                        start.countDown();
                    }
                    catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(error);
                    }
                }
                return ChatDispatchResult.of("answer", AiUsage.empty());
            }
        };
        ChatAppService service = new ChatAppService(capabilities, sessions, messages, quotas, List.of(dispatcher), resolver);
        ChatSendCmd command = new ChatSendCmd();
        command.setQuestion("question");

        var result = service.send(7L, command, null, null, null, null);

        ChatMessage assistant = savedMessages.stream()
                .filter(message -> message.getRole() == online.yudream.base.domain.platform.chat.enumerate.ChatMessageRole.ASSISTANT)
                .findFirst().orElseThrow();
        assertThat(result.reasoning().split(";", -1)).hasSize(callbackCount + 1);
        assertThat(result.tools()).hasSize(callbackCount).isEqualTo(assistant.getTools());
        assertThat(result.activities()).hasSize(callbackCount).isEqualTo(assistant.getActivities());
    }

    @ParameterizedTest
    @EnumSource(value = ChatScopeType.class, names = {"GENERAL", "AGENT"})
    void injectsResolvedWikiContextBeforeGeneralOrAgentDispatch(ChatScopeType scopeType) {
        CapabilityAppService capabilities = mock(CapabilityAppService.class);
        ChatSessionRepo sessions = mock(ChatSessionRepo.class);
        ChatMessageRepo messages = mock(ChatMessageRepo.class);
        ChatQuotaAppService quotas = mock(ChatQuotaAppService.class);
        ChatWikiContextResolver resolver = mock(ChatWikiContextResolver.class);
        AtomicReference<ChatDispatchContext> dispatched = new AtomicReference<>();
        ChatDispatcher dispatcher = new ChatDispatcher() {
            @Override
            public ChatScopeType scopeType() {
                return scopeType;
            }

            @Override
            public ChatDispatchResult dispatch(ChatDispatchContext context) {
                dispatched.set(context);
                return ChatDispatchResult.of("回答", AiUsage.empty());
            }
        };
        when(quotas.todayQuota(7L)).thenReturn(UserChatQuota.of(7L, LocalDate.now(), 1000));
        when(quotas.recordUsage(7L, 0)).thenReturn(UserChatQuota.of(7L, LocalDate.now(), 1000));
        List<ChatContextRefDTO> refs = List.of(new ChatContextRefDTO("wiki", "docs", "文档库"));
        List<String> permissionCodes = List.of("platform:chat:view", "platform:wiki:view");
        when(resolver.resolve(refs, null, "用户问题", permissionCodes))
                .thenReturn(new ChatWikiContextResolver.ResolvedContext("\n\nWIKI_CONTEXT", List.of()));
        ChatAppService service = new ChatAppService(capabilities, sessions, messages, quotas, List.of(dispatcher), resolver);
        ChatSendCmd command = new ChatSendCmd();
        command.setScopeType(scopeType);
        command.setQuestion("用户问题");
        command.setContextRefs(refs);
        command.setPermissionCodes(permissionCodes);

        service.streamOnce(7L, command, null, null, null);

        assertThat(dispatched.get().question()).isEqualTo("用户问题\n\nWIKI_CONTEXT");
    }

    private static void runCallback(CountDownLatch ready, CountDownLatch start, Runnable callback) {
        ready.countDown();
        try {
            start.await();
            callback.run();
        }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
