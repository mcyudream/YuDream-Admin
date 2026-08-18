package online.yudream.base.application.platform.chat.support;

import online.yudream.base.domain.platform.ai.enumerate.AiToolMode;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
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
        AiGenerationGateway generationGateway = mock(AiGenerationGateway.class);
        CapabilityModuleRepo capabilityModuleRepo = mock(CapabilityModuleRepo.class);
        CapabilityModule module = mock(CapabilityModule.class);
        when(module.enabled()).thenReturn(true);
        when(module.getConfig()).thenReturn(Map.of("defaultProvider", "provider", "defaultModel", "model"));
        when(capabilityModuleRepo.findByCode("ai")).thenReturn(Optional.of(module));
        when(generationGateway.generateStream(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            invocation.<java.util.function.Consumer<String>>getArgument(1).accept("第一段");
            invocation.<java.util.function.Consumer<String>>getArgument(1).accept("\n\n");
            invocation.<java.util.function.Consumer<String>>getArgument(2).accept("分析一");
            invocation.<java.util.function.Consumer<String>>getArgument(2).accept(" 分析二");
            return AiGenerationResult.of("第一段\n\n", List.of(), AiUsage.empty());
        });
        List<String> deltas = new ArrayList<>();
        List<String> reasoning = new ArrayList<>();
        List<Object> activities = new ArrayList<>();
        GeneralChatDispatcher dispatcher = new GeneralChatDispatcher(generationGateway, capabilityModuleRepo);
        ChatDispatchContext context = new ChatDispatchContext(
                "question", null, null, null, null, List.of(), List.of(), List.of(),
                deltas::add, reasoning::add, ignored -> { }, activities::add);

        ChatDispatchResult result = dispatcher.dispatch(context);

        assertThat(deltas).containsExactly("第一段", "\n\n");
        assertThat(reasoning).containsExactly("分析一", " 分析二");
        assertThat(activities).isEmpty();
        assertThat(result.content()).isEqualTo("第一段\n\n");
    }

    @Test
    void dispatchRemainsIndependentFromPermissionSnapshot() {
        AiGenerationGateway generationGateway = mock(AiGenerationGateway.class);
        CapabilityModuleRepo capabilityModuleRepo = mock(CapabilityModuleRepo.class);
        CapabilityModule module = mock(CapabilityModule.class);
        when(module.enabled()).thenReturn(true);
        when(module.getConfig()).thenReturn(Map.of("defaultProvider", "provider", "defaultModel", "model"));
        when(capabilityModuleRepo.findByCode("ai")).thenReturn(Optional.of(module));
        when(generationGateway.generateStream(any(), any(), any(), any(), any()))
                .thenReturn(AiGenerationResult.of("answer", List.of(), AiUsage.empty()));
        GeneralChatDispatcher dispatcher = new GeneralChatDispatcher(generationGateway, capabilityModuleRepo);
        ChatDispatchContext context = new ChatDispatchContext(
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

        ArgumentCaptor<AiGenerationRequest> requestCaptor = ArgumentCaptor.forClass(AiGenerationRequest.class);
        verify(generationGateway).generateStream(requestCaptor.capture(), any(), any(), any(), any());
        assertThat(result.content()).isEqualTo("answer");
        assertThat(requestCaptor.getValue().toolMode()).isEqualTo(AiToolMode.NONE);
        assertThat(requestCaptor.getValue().systemPrompt()).contains("标准 Markdown");
    }
}
