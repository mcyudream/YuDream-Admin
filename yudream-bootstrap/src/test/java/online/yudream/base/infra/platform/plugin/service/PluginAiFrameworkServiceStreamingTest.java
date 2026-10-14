package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.dto.AgentRunDTO;
import online.yudream.base.application.platform.agent.service.AgentAppService;
import online.yudream.base.application.platform.ai.dto.AiRunDTO;
import online.yudream.base.application.platform.ai.service.AiRunAppService;
import online.yudream.base.domain.platform.agent.enumerate.AgentTraceSource;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.plugin.spi.system.ai.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PluginAiFrameworkServiceStreamingTest {
    @Test void recoveredJobConsumesFinalBusinessResponseAndPreservesTheUiProjection() {
        var runner = mock(AiRunAppService.class);
        var run = mock(AiRunDTO.class);
        when(run.id()).thenReturn("persisted-run");
        when(run.module()).thenReturn("plugin-agent");
        when(run.status()).thenReturn(online.yudream.base.domain.platform.ai.enumerate.AiRunStatus.COMPLETED);
        when(run.content()).thenReturn("前置节点文本最终答案");
        when(run.result()).thenReturn(java.util.Map.of("content", "最终答案"));
        when(run.tools()).thenReturn(List.of());
        when(runner.getInvocationRun(eq(8L), eq("persisted-run"), any())).thenReturn(run);
        var context = new PluginAiExecutionContext(8L, "actor", "connection", "channel", "message", "MENTION", "trace", List.of());
        var snapshot = service(mock(AgentAppService.class), runner).getRun("persisted-run", context);
        assertEquals("前置节点文本最终答案", snapshot.content());
        assertEquals("最终答案", snapshot.response().content());
        assertEquals("persisted-run", snapshot.response().runId());
        verify(runner).getInvocationRun(eq(8L), eq("persisted-run"), argThat(origin -> "connection".equals(origin.connectionId()) && "actor".equals(origin.platformUserId())));
    }
    @Test void nativeChatKeepsStableSessionAndStreamsBeforeReturningItsRunReceipt() {
        var runner = mock(AiRunAppService.class);
        var emitter = mock(AiRunAppService.RunEmitter.class);
        when(emitter.id()).thenReturn("run-inline");
        inline(runner, emitter);
        var capability = mock(online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo.class);
        var module = mock(online.yudream.base.domain.platform.capability.aggregate.CapabilityModule.class);
        when(module.getEnabled()).thenReturn(true);
        when(module.getConfig()).thenReturn(java.util.Map.of());
        when(capability.findByCode("ai")).thenReturn(java.util.Optional.of(module));
        var generation = mock(online.yudream.base.domain.platform.ai.service.AiGenerationGateway.class);
        var generationProvider = provider(generation);
        var harness = mock(online.yudream.base.domain.platform.ai.service.HarnessAgentGateway.class);
        when(harness.run(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            var request = (online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest) invocation.getArgument(0);
            assertTrue(request.sessionId().startsWith("plugin-session:"));
            assertEquals("plugin-chat", request.agentCode());
            ((Consumer<String>) invocation.getArgument(1)).accept("真实增量");
            return new online.yudream.base.domain.platform.ai.valobj.HarnessRunResult("真实增量", "", null, List.of());
        });
        var service = new PluginAiFrameworkService(capability, generationProvider, mock(ObjectProvider.class),
                mock(online.yudream.base.domain.system.user.repo.UserRepo.class), mock(online.yudream.base.domain.system.user.repo.RoleRepo.class),
                mock(PluginAiToolRegistry.class), mock(online.yudream.base.infra.platform.ai.service.provider.AiProviderConfigParser.class),
                mock(AgentAppService.class), provider(runner));
        service.setHarnessProvider(provider(harness));
        var deltas = new ArrayList<String>();
        var request = new PluginAiChatRequest("system", "new message", "provider", "model", List.of(), null, false, "plugin-session");
        var response = service.chatStream(request, deltas::add).toCompletableFuture().join();
        assertEquals(List.of("真实增量"), deltas);
        assertEquals("run-inline", response.runId());
        assertEquals("COMPLETED", response.status());
        verify(emitter).allowNativeResume();
        verify(emitter).input("new message");
        verifyNoInteractions(generation);
    }

    @Test void waitingIsAPhaseAndLegacyCallCannotMistakeItForFinalSuccess() {
        var agent = mock(AgentAppService.class);
        when(agent.debugByCode(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AgentRunDTO.builder().content("审批前已生成").toolResults(List.of()).build());
        var runner = mock(AiRunAppService.class);
        var emitter = mock(AiRunAppService.RunEmitter.class);
        when(emitter.id()).thenReturn("run-inline");
        when(emitter.awaitingInteraction()).thenReturn(true);
        inline(runner, emitter);
        var service = service(agent, runner);
        var handle = service.startAgent("bot", request());
        assertEquals("run-inline", handle.runId());
        assertEquals("WAITING", handle.result().toCompletableFuture().join().status());
        var error = assertThrows(java.util.concurrent.CompletionException.class,
                () -> service.runAgent("bot", request()).toCompletableFuture().join());
        assertEquals("run-inline", ((PluginAiInteractionRequiredException) error.getCause()).runId());
        verify(runner, never()).cancel(anyLong(), anyString());
    }

    @Test void missingSandboxCannotFallBackToRealPluginExecution() {
        var agent = mock(AgentAppService.class);
        var runner = mock(AiRunAppService.class);
        inline(runner, mock(AiRunAppService.RunEmitter.class));
        var service = service(agent, runner);
        service.setSandboxSessions(mock(online.yudream.base.domain.platform.milky.sandbox.QqSandboxSessionRepo.class));
        var context = new PluginAiExecutionContext(null, "actor", "devtools-sandbox:expired", "group", "msg", "MENTION", "trace", List.of());
        assertThrows(online.yudream.base.domain.common.exception.BizException.class,
                () -> service.startAgent("bot", new PluginAiChatRequest("system", "user", null, null, List.of(), context, true)));
        verifyNoInteractions(agent);
    }
    @Test void streamsAgentDeltasAndPersistsProgressEvenWhenObserverDisconnects() {
        var agent = mock(AgentAppService.class);
        var emitter = mock(AiRunAppService.RunEmitter.class);
        var runner = mock(AiRunAppService.class);
        inline(runner, emitter);
        when(agent.debugByCode(eq("web-card"), any(AgentRunCmd.class), eq(AgentTraceSource.PLUGIN), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    Consumer<String> delta = invocation.getArgument(4);
                    delta.accept("first "); delta.accept("second");
                    return AgentRunDTO.builder().content("first second").toolResults(List.of(
                            new AiAgentToolResult("inspect-page", "inspect", null, "done", null))).build();
                });
        var service = service(agent, runner);
        var deltas = new ArrayList<String>();
        var response = service.runAgentStream("web-card", request(), delta -> {
            deltas.add(delta); throw new IllegalStateException("client disconnected");
        }).toCompletableFuture().join();
        assertEquals(List.of("first ", "second"), deltas);
        assertEquals("first second", response.content());
        assertEquals("inspect", response.toolResults().getFirst().action());
        verify(emitter).content("first "); verify(emitter).content("second");
        verify(runner).start(eq(0L), eq("plugin-agent"), isNull(), any(), any());
    }

    @Test void defaultStreamingFailsWithoutReplayingABlockingAnswer() {
        var blocked = new java.util.concurrent.atomic.AtomicBoolean();
        PluginAiService legacy = new PluginAiService() {
            public CompletionStage<PluginAiChatResponse> chat(PluginAiChatRequest request) { blocked.set(true); return CompletableFuture.completedFuture(new PluginAiChatResponse("complete", List.of())); }
            public CompletionStage<PluginAiChatResponse> runAgent(String code, PluginAiChatRequest request) { return chat(request); }
            public List<PluginAiToolDescriptor> tools() { return List.of(); }
            public List<PluginAiProviderOption> providers() { return List.of(); }
            public List<PluginAiAgentOption> agents() { return List.of(); }
        };
        var deltas = new ArrayList<String>();
        var error = assertThrows(java.util.concurrent.CompletionException.class,
                () -> legacy.runAgentStream("web-card", request(), deltas::add).toCompletableFuture().join());
        assertInstanceOf(UnsupportedOperationException.class, error.getCause());
        assertFalse(blocked.get()); assertTrue(deltas.isEmpty());
    }

    @Test void runAgentMapsAllowedToolsAndExplicitCancellationStopsTheDurableRun() {
        var captured = new java.util.concurrent.atomic.AtomicReference<AgentRunCmd>();
        var agent = mock(AgentAppService.class);
        var runner = mock(AiRunAppService.class);
        inline(runner, mock(AiRunAppService.RunEmitter.class));
        when(agent.debugByCode(eq("bot"), any(AgentRunCmd.class), eq(AgentTraceSource.PLUGIN), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    captured.set(invocation.getArgument(1));
                    return AgentRunDTO.builder().content("ok").toolResults(List.of()).build();
                });
        var context = new PluginAiExecutionContext(null, "10001", "conn", "group", "msg", "MENTION", "trace", List.of(), List.of("wiki.search"));
        var service = service(agent, runner);
        service.runAgent("bot", new PluginAiChatRequest("system", "user", null, null, List.of(), context, true)).toCompletableFuture().join();
        assertEquals(List.of("wiki.search"), captured.get().getRuntimeToolCodes());
        assertTrue(captured.get().isRuntimeToolCallingEnabled());

        var receipt = mock(AiRunDTO.class); when(receipt.id()).thenReturn("run-pending");
        doReturn(receipt).when(runner).start(anyLong(), anyString(), nullable(String.class), any(), any());
        var future = service.runAgent("bot", request()).toCompletableFuture();
        assertFalse(future.isDone()); future.cancel(true);
        verify(runner).cancel(0L, "run-pending");
    }

    @Test void pluginWildcardCannotInventHostPermissionsWithoutAnAuthenticatedAccount() {
        var agent = mock(AgentAppService.class);
        var runner = mock(AiRunAppService.class);
        inline(runner, mock(AiRunAppService.RunEmitter.class));
        var command = new java.util.concurrent.atomic.AtomicReference<AgentRunCmd>();
        when(agent.debugByCode(eq("bot"), any(AgentRunCmd.class), eq(AgentTraceSource.PLUGIN), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    command.set(invocation.getArgument(1));
                    return AgentRunDTO.builder().content("ok").toolResults(List.of()).build();
                });
        var execution = new PluginAiExecutionContext(null, "external-user", "conn", "group", "msg", "MENTION", "trace", List.of("*"));
        service(agent, runner).runAgent("bot", new PluginAiChatRequest("system", "user", null, null, List.of(), execution, true))
                .toCompletableFuture().join();
        assertTrue(command.get().getPermissionCodes().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private void inline(AiRunAppService runner, AiRunAppService.RunEmitter emitter) {
        when(runner.start(anyLong(), anyString(), nullable(String.class), any(), any())).thenAnswer(invocation -> {
            Function<AiRunAppService.RunEmitter, Object> action = invocation.getArgument(3); action.apply(emitter);
            var receipt = mock(AiRunDTO.class); when(receipt.id()).thenReturn("run-inline"); return receipt;
        });
    }
    @SuppressWarnings("unchecked")
    private PluginAiFrameworkService service(AgentAppService agent, AiRunAppService runner) {
        ObjectProvider<AiRunAppService> provider = mock(ObjectProvider.class); when(provider.getIfAvailable()).thenReturn(runner);
        return new PluginAiFrameworkService(mock(online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo.class),
                mock(ObjectProvider.class), mock(ObjectProvider.class), mock(online.yudream.base.domain.system.user.repo.UserRepo.class),
                mock(online.yudream.base.domain.system.user.repo.RoleRepo.class), mock(PluginAiToolRegistry.class),
                mock(online.yudream.base.infra.platform.ai.service.provider.AiProviderConfigParser.class), agent, provider);
    }
    private PluginAiChatRequest request() { return new PluginAiChatRequest("system", "user", "provider", "model", List.of(), null, true); }
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
