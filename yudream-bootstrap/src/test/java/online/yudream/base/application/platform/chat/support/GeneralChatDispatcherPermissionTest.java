package online.yudream.base.application.platform.chat.support;

import online.yudream.base.domain.platform.ai.service.HarnessAgentGateway;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunResult;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeneralChatDispatcherPermissionTest {

    @Test
    void dispatchesReasoningSeparatelyAndKeepsMarkdownWhitespace() {
        HarnessAgentGateway harnessAgents = mock(HarnessAgentGateway.class);
        CapabilityModuleRepo capabilityModuleRepo = mock(CapabilityModuleRepo.class);
        CapabilityModule module = mock(CapabilityModule.class);
        when(module.enabled()).thenReturn(true);
        when(module.getConfig()).thenReturn(Map.of("defaultProvider", "provider", "defaultModel", "model"));
        when(capabilityModuleRepo.findByCode("ai")).thenReturn(Optional.of(module));
        when(harnessAgents.run(any(), any(), any(), any())).thenAnswer(invocation -> {
            invocation.<java.util.function.Consumer<String>>getArgument(1).accept("第一段");
            invocation.<java.util.function.Consumer<String>>getArgument(1).accept("\n\n");
            invocation.<java.util.function.Consumer<String>>getArgument(2).accept("分析一");
            invocation.<java.util.function.Consumer<String>>getArgument(2).accept(" 分析二");
            return new HarnessRunResult("第一段\n\n", "分析一 分析二", AiUsage.empty(), List.of());
        });
        List<String> deltas = new ArrayList<>();
        List<String> reasoning = new ArrayList<>();
        List<Object> activities = new ArrayList<>();
        GeneralChatDispatcher dispatcher = new GeneralChatDispatcher(harnessAgents, capabilityModuleRepo);
        ChatDispatchContext context = new ChatDispatchContext(
                1L, null, "question", null, null, null, null, List.of(), List.of(), List.of(),
                deltas::add, reasoning::add, ignored -> { }, activities::add);

        ChatDispatchResult result = dispatcher.dispatch(context);

        assertThat(deltas).containsExactly("第一段", "\n\n");
        assertThat(reasoning).containsExactly("分析一", " 分析二");
        assertThat(activities).isEmpty();
        assertThat(result.content()).isEqualTo("第一段\n\n");
    }

    @Test
    void dispatchRemainsIndependentFromPermissionSnapshot() {
        HarnessAgentGateway harnessAgents = mock(HarnessAgentGateway.class);
        CapabilityModuleRepo capabilityModuleRepo = mock(CapabilityModuleRepo.class);
        CapabilityModule module = mock(CapabilityModule.class);
        when(module.enabled()).thenReturn(true);
        when(module.getConfig()).thenReturn(Map.of("defaultProvider", "provider", "defaultModel", "model"));
        when(capabilityModuleRepo.findByCode("ai")).thenReturn(Optional.of(module));
        when(harnessAgents.run(any(), any(), any(), any()))
                .thenReturn(new HarnessRunResult("answer", "", AiUsage.empty(), List.of()));
        GeneralChatDispatcher dispatcher = new GeneralChatDispatcher(harnessAgents, capabilityModuleRepo);
        ChatDispatchContext context = new ChatDispatchContext(
                1L,
                null,
                "question",
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of("platform:wiki:search"),
                ignored -> {
                },
                ignored -> {
                },
                ignored -> {
                },
                ignored -> {
                });

        ChatDispatchResult result = dispatcher.dispatch(context);

        ArgumentCaptor<HarnessRunRequest> requestCaptor = ArgumentCaptor.forClass(HarnessRunRequest.class);
        verify(harnessAgents).run(requestCaptor.capture(), any(), any(), any());
        assertThat(result.content()).isEqualTo("answer");
        assertThat(requestCaptor.getValue().toolCallingEnabled()).isFalse();
        assertThat(requestCaptor.getValue().systemPrompt()).contains("标准 Markdown");
    }
}
