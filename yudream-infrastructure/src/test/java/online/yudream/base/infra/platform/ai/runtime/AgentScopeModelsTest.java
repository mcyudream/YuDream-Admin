package online.yudream.base.infra.platform.ai.runtime;

import io.agentscope.core.model.Model;
import io.agentscope.core.model.transport.ProxyConfig;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.compat.deepseek.DeepSeekFormatter;
import io.agentscope.extensions.model.openai.compat.kimi.KimiFormatter;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import online.yudream.base.infra.platform.ai.service.AiClientProperties;
import online.yudream.base.infra.platform.ai.service.provider.AiModelEndpoint;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderEndpoint;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderType;
import online.yudream.base.infra.platform.ai.service.provider.ResolvedAiModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentScopeModelsTest {

    private final AiClientProperties properties = new AiClientProperties();
    private final AgentScopeModels factory = new AgentScopeModels(properties);

    @Test
    void buildsOpenAiCompatibleModelFromResolvedEndpoint() {
        Model model = factory.resolve(resolved(AiProviderType.OPENAI_COMPATIBLE, "deepseek-chat", null));
        assertNotNull(model);
        assertEquals("deepseek-chat", model.getModelName());
        assertTrue(model instanceof OpenAIChatModel);
    }

    @Test
    void selectsCompatFormatterByProviderType() {
        assertTrue(AgentScopeModels.formatterFor(AiProviderType.OPENAI) instanceof OpenAIChatFormatter);
        assertTrue(AgentScopeModels.formatterFor(AiProviderType.OPENAI_COMPATIBLE) instanceof OpenAIChatFormatter);
        assertTrue(AgentScopeModels.formatterFor(AiProviderType.KIMI) instanceof KimiFormatter);
        assertTrue(AgentScopeModels.formatterFor(AiProviderType.DEEPSEEK) instanceof DeepSeekFormatter);
    }

    @Test
    void kimiAndDeepSeekDisableNativeStructuredOutputWithTools() {
        Model kimi = factory.resolve(resolved(AiProviderType.KIMI, "kimi-k2", null));
        Model deepseek = factory.resolve(resolved(AiProviderType.DEEPSEEK, "deepseek-chat", null));
        assertTrue(kimi instanceof OpenAIChatModel openAi && !openAi.supportsNativeStructuredOutputWithTools());
        assertTrue(deepseek instanceof OpenAIChatModel openAi2 && !openAi2.supportsNativeStructuredOutputWithTools());
    }

    @Test
    void cachesModelInstancePerEndpointAndModelIdentity() {
        ResolvedAiModel first = resolved(AiProviderType.OPENAI_COMPATIBLE, "deepseek-chat", null);
        ResolvedAiModel second = resolved(AiProviderType.OPENAI_COMPATIBLE, "deepseek-chat", null);
        ResolvedAiModel other = resolved(AiProviderType.OPENAI_COMPATIBLE, "deepseek-r1", null);
        assertSame(factory.resolve(first), factory.resolve(second));
        assertNotSame(factory.resolve(first), factory.resolve(other));
    }

    @Test
    void parsesHttpProxyUrl() {
        ProxyConfig config = AgentScopeModels.proxyConfig("http://127.0.0.1:7890");
        assertNotNull(config);
        assertEquals("127.0.0.1", config.getHost());
        assertEquals(7890, config.getPort());
        assertNull(AgentScopeModels.proxyConfig("  "));
        assertNull(AgentScopeModels.proxyConfig(null));
    }

    @Test
    void reusesCacheWhenOnlyProxyCaseChanges() {
        ResolvedAiModel lower = resolved(AiProviderType.OPENAI_COMPATIBLE, "m", "http://127.0.0.1:7890");
        ResolvedAiModel upper = resolved(AiProviderType.OPENAI_COMPATIBLE, "m", "HTTP://127.0.0.1:7890");
        assertSame(factory.resolve(lower), factory.resolve(upper));
    }

    private ResolvedAiModel resolved(AiProviderType type, String modelName, String proxyUrl) {
        AiProviderEndpoint provider = new AiProviderEndpoint(
                "p1", "测试供应商", type, "https://api.example.com/v1", null, "sk-test", proxyUrl,
                modelName, null, null, List.of(), List.of(), List.of(), true);
        AiModelEndpoint model = new AiModelEndpoint(
                "m1", modelName, modelName, null, null, null, null, "chat", false);
        return new ResolvedAiModel(provider, model, null);
    }
}
