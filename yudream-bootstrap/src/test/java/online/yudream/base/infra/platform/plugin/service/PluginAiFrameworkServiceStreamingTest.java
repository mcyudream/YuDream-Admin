package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.dto.AgentRunDTO;
import online.yudream.base.application.platform.agent.service.AgentAppService;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.plugin.spi.system.ai.PluginAiAgentOption;
import online.yudream.base.plugin.spi.system.ai.PluginAiChatRequest;
import online.yudream.base.plugin.spi.system.ai.PluginAiChatResponse;
import online.yudream.base.plugin.spi.system.ai.PluginAiProviderOption;
import online.yudream.base.plugin.spi.system.ai.PluginAiService;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PluginAiFrameworkServiceStreamingTest {

    @Test
    void streamsAgentDeltasAndReturnsTheFinalResponse() {
        AgentAppService agentAppService = mock(AgentAppService.class);
        when(agentAppService.debugByCode(eq("web-card"), any(AgentRunCmd.class), eq(null), any(), eq(null)))
                .thenAnswer(invocation -> {
                    Consumer<String> onDelta = invocation.getArgument(3);
                    onDelta.accept("first ");
                    onDelta.accept("second");
                    return AgentRunDTO.builder()
                            .content("first second")
                            .toolResults(List.of(new AiAgentToolResult(
                                    "inspect-page", "inspect", null, "done", null
                            )))
                            .build();
                });
        PluginAiFrameworkService service = new PluginAiFrameworkService(
                mock(online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo.class),
                mock(ObjectProvider.class),
                mock(online.yudream.base.domain.system.user.repo.UserRepo.class),
                mock(online.yudream.base.domain.system.user.repo.RoleRepo.class),
                mock(PluginAiToolRegistry.class),
                mock(online.yudream.base.infra.platform.ai.service.provider.AiProviderConfigParser.class),
                agentAppService
        );
        List<String> deltas = new ArrayList<>();

        PluginAiChatResponse response = service.runAgentStream("web-card", request(), deltas::add)
                .toCompletableFuture()
                .join();

        assertEquals(List.of("first ", "second"), deltas);
        assertEquals("first second", response.content());
        assertEquals("inspect", response.toolResults().getFirst().action());
    }

    @Test
    void defaultStreamingFallbackKeepsExistingImplementationsCompatible() {
        PluginAiChatResponse expected = new PluginAiChatResponse("complete", List.of());
        PluginAiService legacyImplementation = new PluginAiService() {
            @Override
            public CompletionStage<PluginAiChatResponse> chat(PluginAiChatRequest request) {
                return CompletableFuture.completedFuture(expected);
            }

            @Override
            public List<PluginAiToolDescriptor> tools() {
                return List.of();
            }

            @Override
            public List<PluginAiProviderOption> providers() {
                return List.of();
            }

            @Override
            public List<PluginAiAgentOption> agents() {
                return List.of();
            }

            @Override
            public CompletionStage<PluginAiChatResponse> runAgent(String agentCode, PluginAiChatRequest request) {
                return CompletableFuture.completedFuture(expected);
            }
        };
        List<String> deltas = new ArrayList<>();

        PluginAiChatResponse actual = legacyImplementation.runAgentStream("web-card", request(), deltas::add)
                .toCompletableFuture()
                .join();

        assertSame(expected, actual);
        assertEquals(List.of("complete"), deltas);
    }

    private static PluginAiChatRequest request() {
        return new PluginAiChatRequest(
                "system",
                "user",
                "provider",
                "model",
                List.of(),
                null,
                true
        );
    }
}
