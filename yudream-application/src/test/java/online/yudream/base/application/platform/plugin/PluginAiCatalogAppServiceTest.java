package online.yudream.base.application.platform.plugin;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.plugin.dto.PluginAiAgentCatalogDTO;
import online.yudream.base.application.platform.plugin.dto.PluginAiProviderCatalogDTO;
import online.yudream.base.application.platform.plugin.service.PluginAiCatalogAppService;
import online.yudream.base.plugin.spi.system.ai.PluginAiAgentOption;
import online.yudream.base.plugin.spi.system.ai.PluginAiChatRequest;
import online.yudream.base.plugin.spi.system.ai.PluginAiChatResponse;
import online.yudream.base.plugin.spi.system.ai.PluginAiModelOption;
import online.yudream.base.plugin.spi.system.ai.PluginAiProviderOption;
import online.yudream.base.plugin.spi.system.ai.PluginAiService;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginAiCatalogAppServiceTest {

    @Test
    void returnsEmptyWhenAiCapabilityDisabled() {
        AtomicInteger calls = new AtomicInteger();
        PluginAiCatalogAppService service = new PluginAiCatalogAppService(
                new ToggleCapabilityAppService(false),
                new StubAiService(calls, List.of(), List.of()));

        assertTrue(service.agents().isEmpty());
        assertTrue(service.providers().isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void mapsAgentsAndProviders() {
        PluginAiCatalogAppService service = new PluginAiCatalogAppService(
                new ToggleCapabilityAppService(true),
                new StubAiService(new AtomicInteger(),
                        List.of(new PluginAiAgentOption("wiki", "Wiki 助手", "答疑")),
                        List.of(new PluginAiProviderOption("openai", "OpenAI",
                                List.of(new PluginAiModelOption("gpt-4.1", "GPT 4.1"))))));

        List<PluginAiAgentCatalogDTO> agents = service.agents();
        List<PluginAiProviderCatalogDTO> providers = service.providers();

        assertEquals(1, agents.size());
        assertEquals("wiki", agents.getFirst().getCode());
        assertEquals("Wiki 助手", agents.getFirst().getName());
        assertEquals("答疑", agents.getFirst().getDescription());
        assertEquals(1, providers.size());
        assertEquals("openai", providers.getFirst().getCode());
        assertEquals("gpt-4.1", providers.getFirst().getModels().getFirst().getCode());
    }

    private static final class ToggleCapabilityAppService extends CapabilityAppService {
        private final boolean enabled;

        private ToggleCapabilityAppService(boolean enabled) {
            super(null, List.of());
            this.enabled = enabled;
        }

        @Override
        public boolean enabled(String code) {
            return enabled && "ai".equals(code);
        }
    }

    private static final class StubAiService implements PluginAiService {
        private final AtomicInteger calls;
        private final List<PluginAiAgentOption> agents;
        private final List<PluginAiProviderOption> providers;

        private StubAiService(AtomicInteger calls,
                              List<PluginAiAgentOption> agents,
                              List<PluginAiProviderOption> providers) {
            this.calls = calls;
            this.agents = agents;
            this.providers = providers;
        }

        @Override
        public CompletionStage<PluginAiChatResponse> chat(PluginAiChatRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PluginAiToolDescriptor> tools() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PluginAiProviderOption> providers() {
            calls.incrementAndGet();
            return providers;
        }

        @Override
        public List<PluginAiAgentOption> agents() {
            calls.incrementAndGet();
            return agents;
        }

        @Override
        public CompletionStage<PluginAiChatResponse> runAgent(String agentCode, PluginAiChatRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
