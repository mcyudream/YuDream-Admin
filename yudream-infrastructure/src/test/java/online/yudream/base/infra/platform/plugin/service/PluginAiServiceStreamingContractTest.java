package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.plugin.spi.system.ai.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PluginAiServiceStreamingContractTest {

    @Test
    void missingStreamingImplementationFailsWithoutReplayingBlockingOutput() {
        AtomicBoolean blockingCalled = new AtomicBoolean();
        AtomicBoolean deltaReceived = new AtomicBoolean();
        AtomicBoolean toolReceived = new AtomicBoolean();
        PluginAiService service = new BlockingOnlyService(blockingCalled);
        PluginAiChatRequest request = new PluginAiChatRequest("系统提示", "问题", null, null,
                List.of(), null, false);

        CompletionException chatFailure = assertThrows(CompletionException.class, () ->
                service.chatStream(request, delta -> deltaReceived.set(true), tool -> toolReceived.set(true))
                        .toCompletableFuture().join());
        CompletionException agentFailure = assertThrows(CompletionException.class, () ->
                service.runAgentStream("test", request, delta -> deltaReceived.set(true))
                        .toCompletableFuture().join());

        assertInstanceOf(UnsupportedOperationException.class, chatFailure.getCause());
        assertInstanceOf(UnsupportedOperationException.class, agentFailure.getCause());
        assertFalse(blockingCalled.get());
        assertFalse(deltaReceived.get());
        assertFalse(toolReceived.get());
    }

    private record BlockingOnlyService(AtomicBoolean blockingCalled) implements PluginAiService {
        @Override
        public CompletionStage<PluginAiChatResponse> chat(PluginAiChatRequest request) {
            blockingCalled.set(true);
            return CompletableFuture.completedFuture(new PluginAiChatResponse("整段结果", List.of()));
        }

        @Override
        public CompletionStage<PluginAiChatResponse> runAgent(String agentCode, PluginAiChatRequest request) {
            return chat(request);
        }

        @Override public List<PluginAiToolDescriptor> tools() { return List.of(); }
        @Override public List<PluginAiProviderOption> providers() { return List.of(); }
        @Override public List<PluginAiAgentOption> agents() { return List.of(); }
    }
}
